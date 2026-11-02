package br.com.materia.service;

import br.com.materia.domain.*;
import br.com.materia.infra.Database;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class InventoryService {
    public static final List<String> UNITS = List.of("UN", "KG", "G", "L", "ML", "M", "M²", "M³", "CX", "PCT", "PAR");
    private final Database db;
    public InventoryService(Database db) { this.db = db; }
    public Database database() { return db; }

    @FunctionalInterface private interface Work<T> { T run(Connection c) throws Exception; }
    private <T> T transaction(Work<T> work) throws Exception {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try { T result = work.run(c); c.commit(); return result; }
            catch (Exception ex) { c.rollback(); throw friendly(ex); }
        }
    }
    private Exception friendly(Exception ex) {
        if (ex instanceof SQLException && ex.getMessage()!=null) {
            if (ex.getMessage().contains("materials.sku")) return new IllegalArgumentException("Já existe um material com esse código.");
            if (ex.getMessage().contains("materials.barcode")) return new IllegalArgumentException("Esse código de barras já está cadastrado.");
            if (ex.getMessage().contains("suppliers.document")) return new IllegalArgumentException("Esse documento já pertence a um fornecedor.");
        }
        return ex;
    }
    public List<Material> materials() throws Exception {
        try (Connection c = db.connect()) { return materials(c); }
    }
    private List<Material> materials(Connection c) throws SQLException {
        List<Material> list = new ArrayList<>();
        try (Statement p = c.createStatement(); ResultSet r = p.executeQuery("SELECT m.*, COALESCE(s.name,'') supplier_name FROM materials m LEFT JOIN suppliers s ON s.id=m.supplier_id ORDER BY m.active DESC,m.name COLLATE NOCASE")) {
            while (r.next()) list.add(mapMaterial(r));
        }
        return list;
    }
    private Material mapMaterial(ResultSet r) throws SQLException {
        Object supplier = r.getObject("supplier_id");
        return new Material(r.getLong("id"), r.getString("sku"), r.getString("name"), r.getString("category"), r.getString("unit"),
                r.getString("location"), supplier==null?null:((Number)supplier).longValue(), r.getString("supplier_name"),
                r.getString("barcode"), r.getLong("cost_cents"), r.getLong("price_cents"), r.getLong("minimum_milli"),
                r.getLong("stock_milli"), r.getString("notes"), r.getInt("active")==1);
    }
    private Material find(Connection c, long id) throws Exception {
        try (PreparedStatement p=c.prepareStatement("SELECT m.*,COALESCE(s.name,'') supplier_name FROM materials m LEFT JOIN suppliers s ON s.id=m.supplier_id WHERE m.id=?")) {
            p.setLong(1,id);
            try(ResultSet r=p.executeQuery()) {if(!r.next())throw new IllegalArgumentException("Material não encontrado.");return mapMaterial(r);}
        }
    }
    public long saveMaterial(Material input, long initialStock, String operator) throws Exception {
        validateMaterial(input, initialStock);
        return transaction(c -> saveMaterial(c, input, initialStock, operator));
    }
    private long saveMaterial(Connection c, Material m, long initial, String operator) throws Exception {
        Material old = m.id()==0 ? null : find(c,m.id());
        if (old!=null && !old.active()) throw new IllegalArgumentException("Reative o material antes de editar.");
        if (old!=null && !old.unit().equals(m.unit()) && (old.stock()!=0 || movementCount(c,old.id())>0))
            throw new IllegalArgumentException("A unidade não pode mudar após uma movimentação. Cadastre outro código para a nova unidade.");
        if (m.supplierId()!=null && (old==null || !Objects.equals(old.supplierId(),m.supplierId()))) {
            try (PreparedStatement p = c.prepareStatement("SELECT active FROM suppliers WHERE id=?")) {
                p.setLong(1,m.supplierId());
                try (ResultSet r = p.executeQuery()) { if (!r.next() || r.getInt(1)!=1) throw new IllegalArgumentException("Escolha um fornecedor ativo."); }
            }
        }
        String sql = old==null ? "INSERT INTO materials(sku,name,category,unit,location,supplier_id,barcode,cost_cents,price_cents,minimum_milli,notes,updated_at,stock_milli,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0,?)" :
                "UPDATE materials SET sku=?,name=?,category=?,unit=?,location=?,supplier_id=?,barcode=?,cost_cents=?,price_cents=?,minimum_milli=?,notes=?,updated_at=? WHERE id=?";
        long id;
        String now = LocalDateTime.now().toString();
        try (PreparedStatement p = c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1,m.sku().strip().toUpperCase(Locale.ROOT)); p.setString(2,m.name().strip()); p.setString(3,m.category().strip());
            p.setString(4,m.unit()); p.setString(5,m.location().strip());
            if (m.supplierId()==null) p.setNull(6,Types.INTEGER); else p.setLong(6,m.supplierId());
            p.setString(7,m.barcode().strip()); p.setLong(8,m.costCents()); p.setLong(9,m.priceCents());
            p.setLong(10,m.minimum()); p.setString(11,m.notes().strip()); p.setString(12,now);
            if (old==null) p.setString(13,now); else p.setLong(13,m.id());
            p.executeUpdate();
            if (old==null) { try (ResultSet r=p.getGeneratedKeys()) { r.next(); id=r.getLong(1); } } else id=m.id();
        }
        if (old==null && initial>0) move(c,id,"ENTRY",initial,m.costCents(),"Saldo inicial","",operator);
        return id;
    }
    private long movementCount(Connection c, long id) throws SQLException {
        try (PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM movements WHERE material_id=?")) {
            p.setLong(1,id); try (ResultSet r=p.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
    private static String text(String value, String field, int min, int max) {
        if (value==null || value.strip().length()<min || value.strip().length()>max)
            throw new IllegalArgumentException(field + " deve ter entre " + min + " e " + max + " caracteres.");
        return value.strip();
    }
    public static void validateMaterial(Material m, long initial) {
        text(m.sku(),"Código",1,40); text(m.name(),"Nome",2,120); text(m.category(),"Categoria",0,80);
        text(m.location(),"Localização",0,100); text(m.barcode(),"Código de barras",0,80); text(m.notes(),"Observações",0,2000);
        if (!UNITS.contains(m.unit())) throw new IllegalArgumentException("Unidade inválida.");
        if (m.costCents()<0 || m.priceCents()<0 || m.costCents()>Numbers.MAX_MONEY || m.priceCents()>Numbers.MAX_MONEY)
            throw new IllegalArgumentException("Custo ou preço fora do limite.");
        if (m.minimum()<0 || initial<0 || m.minimum()>Numbers.MAX_QUANTITY || initial>Numbers.MAX_QUANTITY)
            throw new IllegalArgumentException("Quantidade fora do limite.");
        if (m.id()!=0 && initial!=0) throw new IllegalArgumentException("O estoque deve ser alterado em Movimentações.");
    }
    public void setMaterialActive(long id, boolean active) throws Exception {
        transaction(c -> {
            Material m=find(c,id);
            if (!active && m.stock()!=0) throw new IllegalArgumentException("Zere o saldo por uma movimentação antes de arquivar.");
            try (PreparedStatement p=c.prepareStatement("UPDATE materials SET active=?,updated_at=? WHERE id=?")) {
                p.setInt(1,active?1:0); p.setString(2,LocalDateTime.now().toString()); p.setLong(3,id); p.executeUpdate();
            }
            return null;
        });
    }
    public void move(long id, String type, long quantity, long cost, String reason, String reference, String operator) throws Exception {
        transaction(c -> { move(c,id,type,quantity,cost,reason,reference,operator); return null; });
    }
    private void move(Connection c, long id, String type, long quantity, long cost, String reason, String reference, String operator) throws Exception {
        if (!List.of("ENTRY","EXIT","ADJUST").contains(type)) throw new IllegalArgumentException("Tipo de movimentação inválido.");
        if (quantity<0 || quantity>Numbers.MAX_QUANTITY || (!type.equals("ADJUST") && quantity==0))
            throw new IllegalArgumentException("Informe uma quantidade maior que zero dentro do limite permitido.");
        if (cost<0 || cost>Numbers.MAX_MONEY) throw new IllegalArgumentException("Custo fora do limite.");
        text(reason,"Motivo",3,300); text(reference,"Documento/referência",0,100); text(operator,"Responsável",2,80);
        Material old=find(c,id);
        if (!old.active()) throw new IllegalArgumentException("Material arquivado. Reative-o para movimentar.");
        long delta = switch (type) { case "ENTRY" -> quantity; case "EXIT" -> -quantity; default -> quantity-old.stock(); };
        if (delta==0) throw new IllegalArgumentException("O saldo informado é igual ao saldo atual.");
        long balance=Math.addExact(old.stock(),delta);
        if (balance<0) throw new IllegalArgumentException("Estoque insuficiente. Saldo atual: " + Numbers.qty(old.stock()) + " " + old.unit() + ".");
        if (balance>Numbers.MAX_QUANTITY) throw new IllegalArgumentException("O saldo excede o limite permitido.");
        long nextCost=type.equals("ENTRY") ? Numbers.weightedCost(old.stock(),old.costCents(),quantity,cost) : old.costCents();
        String now=LocalDateTime.now().toString();
        try (PreparedStatement p=c.prepareStatement("UPDATE materials SET stock_milli=?,cost_cents=?,updated_at=? WHERE id=? AND stock_milli=?")) {
            p.setLong(1,balance); p.setLong(2,nextCost); p.setString(3,now); p.setLong(4,id); p.setLong(5,old.stock());
            if (p.executeUpdate()!=1) throw new IllegalStateException("O saldo mudou. Atualize a tela e tente novamente.");
        }
        try (PreparedStatement p=c.prepareStatement("INSERT INTO movements(material_id,type,delta_milli,balance_milli,cost_cents,reason,reference,operator,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
            p.setLong(1,id); p.setString(2,type); p.setLong(3,delta); p.setLong(4,balance); p.setLong(5,type.equals("ENTRY")?cost:old.costCents());
            p.setString(6,reason.strip()); p.setString(7,reference.strip()); p.setString(8,operator.strip()); p.setString(9,now); p.executeUpdate();
        }
    }
    public List<Movement> movements() throws Exception {
        List<Movement> list=new ArrayList<>();
        try (Connection c=db.connect(); Statement p=c.createStatement(); ResultSet r=p.executeQuery("SELECT v.*,m.sku,m.name,m.unit FROM movements v JOIN materials m ON m.id=v.material_id ORDER BY v.id DESC")) {
            while (r.next()) list.add(new Movement(r.getLong("id"),r.getLong("material_id"),r.getString("sku"),r.getString("name"),r.getString("unit"),
                    r.getString("type"),r.getLong("delta_milli"),r.getLong("balance_milli"),r.getLong("cost_cents"),r.getString("reason"),r.getString("reference"),r.getString("operator"),r.getString("created_at")));
        }
        return list;
    }
    public List<Supplier> suppliers() throws Exception {
        List<Supplier> list=new ArrayList<>();
        try (Connection c=db.connect(); Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT * FROM suppliers ORDER BY active DESC,name COLLATE NOCASE")) {
            while(r.next()) list.add(new Supplier(r.getLong("id"),r.getString("name"),r.getString("document"),r.getString("contact"),r.getString("phone"),r.getString("email"),r.getString("address"),r.getString("notes"),r.getInt("active")==1));
        }
        return list;
    }
    public long saveSupplier(Supplier s) throws Exception {
        text(s.name(),"Nome do fornecedor",2,120); text(s.document(),"Documento",0,30); text(s.contact(),"Contato",0,100);
        text(s.phone(),"Telefone",0,40); text(s.email(),"E-mail",0,120); text(s.address(),"Endereço",0,250); text(s.notes(),"Observações",0,2000);
        if (!s.email().isBlank() && !s.email().strip().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("E-mail inválido.");
        return transaction(c -> {
            String sql=s.id()==0 ? "INSERT INTO suppliers(name,document,contact,phone,email,address,notes) VALUES(?,?,?,?,?,?,?)" :
                    "UPDATE suppliers SET name=?,document=?,contact=?,phone=?,email=?,address=?,notes=? WHERE id=?";
            try (PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1,s.name().strip()); p.setString(2,s.document().replaceAll("[ .\\-/]","")); p.setString(3,s.contact().strip());
                p.setString(4,s.phone().strip()); p.setString(5,s.email().strip()); p.setString(6,s.address().strip()); p.setString(7,s.notes().strip());
                if (s.id()!=0) p.setLong(8,s.id());
                if(p.executeUpdate()!=1) throw new IllegalArgumentException("Fornecedor não encontrado.");
                if (s.id()!=0) return s.id();
                try(ResultSet r=p.getGeneratedKeys()) { r.next(); return r.getLong(1); }
            }
        });
    }
    public void setSupplierActive(long id, boolean active) throws Exception {
        transaction(c -> { try(PreparedStatement p=c.prepareStatement("UPDATE suppliers SET active=? WHERE id=?")) {
            p.setInt(1,active?1:0);p.setLong(2,id);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Fornecedor não encontrado.");
        } return null; });
    }
    public int importMaterials(List<Material> inputs, List<Long> initial, String operator) throws Exception {
        if (inputs.isEmpty() || inputs.size()!=initial.size()) throw new IllegalArgumentException("Importação sem materiais válidos.");
        for(int i=0;i<inputs.size();i++) { if(inputs.get(i).id()!=0)throw new IllegalArgumentException("Importação aceita apenas novos materiais."); validateMaterial(inputs.get(i),initial.get(i)); }
        return transaction(c -> { for(int i=0;i<inputs.size();i++) saveMaterial(c,inputs.get(i),initial.get(i),operator); return inputs.size(); });
    }
    public void demo() throws Exception {
        transaction(c -> {
            if(!materials(c).isEmpty()) throw new IllegalArgumentException("A demonstração só pode ser carregada em uma base sem materiais.");
            String[] names={"Papel A4 75g • 500 folhas","Caneta esferográfica azul","Cabo de rede CAT6","Parafuso sextavado M8","Luva de proteção","Álcool isopropílico","Fita isolante 20 m","Filamento PLA preto"};
            String[] cats={"Escritório","Escritório","Tecnologia","Ferragens","Segurança","Limpeza","Elétrica","Tecnologia"};
            String[] units={"PCT","UN","M","UN","PAR","L","UN","KG"};
            long[] stocks={42000,18000,125500,86000,0,4500,27000,2000};
            long[] mins={10000,30000,30000,20000,10000,5000,10000,3000};
            long[] costs={2890,180,350,95,1290,4200,850,8500};
            for(int i=0;i<names.length;i++) {
                Material m=new Material(0,"MAT-"+String.format("%03d",i+1),names[i],cats[i],units[i],"A-0"+(i%3+1),null,"","",costs[i],costs[i]*2,mins[i],0,"Dados fictícios para demonstração.",true);
                saveMaterial(c,m,stocks[i],"Demonstração");
            }
            return null;
        });
    }
}
