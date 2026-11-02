package br.com.materia.ui;

import br.com.materia.domain.*;
import br.com.materia.infra.*;
import br.com.materia.service.InventoryService;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

public final class MainFrame extends JFrame {
    @FunctionalInterface public interface Checked {void run() throws Exception;}
    private static final Logger LOG=Logger.getLogger("Materia");
    private final InventoryService service;
    private final Settings settings;
    private List<Material> materials=List.of();
    private List<Movement> movements=List.of();
    private List<Supplier> suppliers=List.of();
    private final JPanel workspace=Ui.panel(new BorderLayout(0,22));
    private final JLabel toast=Ui.hint("Pronto para trabalhar.");
    private String page="Visão geral";
    private final Map<String,JButton> navigation=new LinkedHashMap<>();
    private JTextField currentSearch;

    public MainFrame(InventoryService service,Settings settings) throws Exception {
        super("Materia • Gestão de materiais");this.service=service;this.settings=settings;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);setMinimumSize(new Dimension(1100,720));setSize(1380,880);setLocationRelativeTo(null);
        try {setIconImage(javax.imageio.ImageIO.read(Objects.requireNonNull(getClass().getResource("/icon.png"))));}catch(Exception ignored){}
        JPanel root=new JPanel(new BorderLayout());root.setBackground(UIManager.getColor("Panel.background"));
        root.add(sidebar(),BorderLayout.WEST);
        JPanel right=Ui.panel(new BorderLayout());workspace.setBorder(new EmptyBorder(28,30,22,30));right.add(workspace);
        JPanel foot=Ui.panel(new BorderLayout());foot.setBorder(new EmptyBorder(7,30,12,30));foot.add(toast);foot.add(Ui.hint("LOCAL  /  SQLite  ·  v1.0.0"),BorderLayout.EAST);right.add(foot,BorderLayout.SOUTH);
        root.add(right);setContentPane(root);shortcuts();reload("");
    }
    public Settings settings(){return settings;}
    private JPanel sidebar(){
        JPanel side=new JPanel(new BorderLayout());side.setPreferredSize(new Dimension(218,0));side.setBackground(Ui.NAVY);side.setBorder(new EmptyBorder(34,18,24,18));
        JPanel top=Ui.panel(new GridLayout(0,1,0,8));JLabel brand=Ui.title("MATERIA",26);brand.setForeground(Color.WHITE);brand.setBorder(new EmptyBorder(0,14,0,0));top.add(brand);
        JLabel tagline=new JLabel("CONTROLE QUE ORGANIZA");tagline.setFont(tagline.getFont().deriveFont(9f));tagline.setForeground(new Color(0x9CB6BC));tagline.setBorder(new EmptyBorder(0,14,20,0));top.add(tagline);side.add(top,BorderLayout.NORTH);
        JPanel nav=Ui.panel(new GridBagLayout());int row=0;
        for(String label:List.of("Visão geral","Materiais","Movimentações","Fornecedores","Relatórios","Sistema")){
            JButton b=Ui.button(label,()->showPage(label));b.setHorizontalAlignment(SwingConstants.LEFT);b.setBorder(new EmptyBorder(15,16,15,8));b.setFont(b.getFont().deriveFont(Font.BOLD,14));b.setForeground(new Color(0xB7C9CE));b.setBackground(Ui.NAVY);b.setBorderPainted(false);
            GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.gridy=row++;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;c.insets=new Insets(0,0,7,0);nav.add(b,c);navigation.put(label,b);
        }
        GridBagConstraints filler=new GridBagConstraints();filler.gridy=row;filler.weighty=1;nav.add(Box.createVerticalGlue(),filler);side.add(nav);
        JLabel local=new JLabel("<html><b>Seu estoque, no lugar.</b><br><br>Dados salvos neste computador.<br>Faça backups regularmente.</html>");local.setForeground(new Color(0xA4BEC4));local.setFont(local.getFont().deriveFont(11f));local.setBorder(new EmptyBorder(20,12,0,4));side.add(local,BorderLayout.SOUTH);return side;
    }
    private void shortcuts(){
        bind("control N","new",()->openMaterial(null));bind("control M","move",()->openMovement(null));bind("control F","find",()->{if(currentSearch!=null)currentSearch.requestFocusInWindow();});bind("F5","refresh",()->attempt(()->reload("Dados atualizados.")));
    }
    private void bind(String key,String id,Runnable r){getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),id);getRootPane().getActionMap().put(id,new AbstractAction(){public void actionPerformed(ActionEvent e){r.run();}});}
    public void attempt(Checked action){
        try{action.run();}catch(Exception ex){LOG.log(Level.WARNING,"Operação não concluída",ex);String message=ex instanceof java.sql.SQLException?"Não foi possível acessar o banco. Feche outras instâncias e verifique o espaço em disco.":ex.getMessage();JOptionPane.showMessageDialog(this,message==null?ex.toString():message,"Verifique os dados",JOptionPane.WARNING_MESSAGE);}
    }
    public boolean confirm(String title,String text){return JOptionPane.showConfirmDialog(this,text,title,JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE)==JOptionPane.YES_OPTION;}
    private void work(String title,Checked action,Checked completed){
        JDialog progress=new JDialog(this,title,true);progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JPanel panel=new JPanel(new BorderLayout(0,16));panel.setBorder(new EmptyBorder(24,28,24,28));panel.add(new JLabel(title+"…"),BorderLayout.NORTH);
        JProgressBar bar=new JProgressBar();bar.setIndeterminate(true);panel.add(bar);progress.setContentPane(panel);progress.setSize(400,140);progress.setLocationRelativeTo(this);
        new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground() throws Exception {action.run();return null;}
            @Override protected void done(){progress.dispose();attempt(()->{try{get();}catch(java.util.concurrent.ExecutionException ex){if(ex.getCause() instanceof Exception cause)throw cause;throw ex;}completed.run();});}
        }.execute();progress.setVisible(true);
    }
    public void reload(String message) throws Exception {
        materials=service.materials();movements=service.movements();suppliers=service.suppliers();render();if(!message.isEmpty())toast.setText(message);
    }
    public void showPage(String name){page=name;attempt(this::render);}
    private void render(){
        workspace.removeAll();currentSearch=null;
        navigation.forEach((name,b)->{b.setBackground(name.equals(page)?new Color(0x23505A):Ui.NAVY);b.setForeground(name.equals(page)?Color.WHITE:new Color(0xB7C9CE));});
        String subtitle=switch(page){case "Materiais"->"Cadastre, localize e acompanhe cada item do seu estoque.";case "Movimentações"->"Rastreabilidade de cada entrada, saída e ajuste de inventário.";case "Fornecedores"->"Centralize contatos e conecte fornecedores aos materiais.";case "Relatórios"->"Transforme o estoque em informação para decidir.";case "Sistema"->"Preferências, proteção dos dados e informações do aplicativo.";default->settings.company()+"  ·  "+LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));};
        JPanel header=Ui.panel(new BorderLayout(16,0)),titles=Ui.panel(new GridLayout(0,1,0,7));titles.add(Ui.title(page,28));titles.add(Ui.hint(subtitle));header.add(titles);
        JPanel quick=Ui.panel(new GridBagLayout());quick.add(Ui.primary(page.equals("Fornecedores")?"+ Fornecedor":"+ Novo material",()->{if(page.equals("Fornecedores"))openSupplier(null);else openMaterial(null);}));header.add(quick,BorderLayout.EAST);workspace.add(header,BorderLayout.NORTH);
        workspace.add(switch(page){case "Materiais"->materialsPage();case "Movimentações"->movementsPage();case "Fornecedores"->suppliersPage();case "Relatórios"->reportsPage();case "Sistema"->settingsPage();default->dashboard();});
        workspace.revalidate();workspace.repaint();
    }
    private void openMaterial(Material m){attempt(()->new MaterialDialog(this,service,m).setVisible(true));}
    private void openSupplier(Supplier s){new SupplierDialog(this,service,s).setVisible(true);}
    private void openMovement(Material m){attempt(()->new MovementDialog(this,service,m).setVisible(true));}
    private JPanel dashboard(){
        List<Material> active=materials.stream().filter(Material::active).toList();long alerts=active.stream().filter(m->m.stock()<=m.minimum()).count();
        BigDecimal value=active.stream().map(m->Numbers.valuation(m.stock(),m.costCents())).reduce(BigDecimal.ZERO,BigDecimal::add);
        JPanel body=Ui.panel(new BorderLayout(0,20));JPanel metrics=Ui.panel(new GridLayout(1,4,14,0));
        metrics.add(metric("MATERIAIS ATIVOS",Integer.toString(active.size()),"Itens do catálogo",Ui.TEAL));
        metrics.add(metric("VALOR EM ESTOQUE",Numbers.currency(value),"Quantidade × custo médio",Ui.TEAL));
        metrics.add(metric("PRECISAM DE ATENÇÃO",Long.toString(alerts),"Saldo igual ou inferior ao mínimo",new Color(0xB7791F)));
        metrics.add(metric("MOVIMENTAÇÕES",Integer.toString(movements.size()),"Histórico registrado",Ui.TEAL));body.add(metrics,BorderLayout.NORTH);
        JPanel main=Ui.panel(new GridLayout(2,1,0,18));JPanel top=Ui.panel(new GridLayout(1,2,18,0));
        JPanel chart=Ui.card();chart.add(Ui.title("Valor por categoria · top 6",17),BorderLayout.NORTH);chart.add(new StockChart(active));top.add(chart);
        JPanel attention=Ui.card();attention.add(Ui.title("Reposição em foco",17),BorderLayout.NORTH);
        JTable low=Ui.table("Material","Saldo","Mínimo");List<Material> missing=active.stream().filter(m->m.stock()<=m.minimum()).sorted(Comparator.comparingLong(Material::stock)).toList();
        Ui.rows(low,missing.stream().map(m->new Object[]{m.name(),Numbers.qty(m.stock())+" "+m.unit(),Numbers.qty(m.minimum())}).toList());Ui.numericSort(low,1,2);attention.add(new JScrollPane(low));
        attention.add(Ui.button(missing.isEmpty()?"Ver materiais":"Registrar entrada",()->{if(missing.isEmpty())showPage("Materiais");else openMovement(selectedOrFirst(low,missing));}),BorderLayout.SOUTH);top.add(attention);main.add(top);
        JPanel recent=Ui.card();JPanel recentTop=Ui.panel(new BorderLayout());recentTop.add(Ui.title("Atividade recente",17));recentTop.add(Ui.button("Ver histórico",()->showPage("Movimentações")),BorderLayout.EAST);recent.add(recentTop,BorderLayout.NORTH);
        JTable latest=Ui.table("Data / hora","Material","Operação","Quantidade","Responsável");Ui.rows(latest,movements.stream().limit(8).map(m->new Object[]{Reports.date(m.createdAt()),m.name(),m.typeLabel(),Numbers.qty(m.delta())+" "+m.unit(),m.operator()}).toList());recent.add(new JScrollPane(latest));
        if(materials.isEmpty())recent.add(Ui.hint("Comece em + Novo material ou carregue dados fictícios em Sistema."),BorderLayout.SOUTH);main.add(recent);body.add(main);return body;
    }
    private JPanel metric(String label,String value,String note,Color accent){JPanel c=Ui.card();JLabel l=Ui.hint(label);l.setFont(l.getFont().deriveFont(Font.BOLD,10f));c.add(l,BorderLayout.NORTH);JLabel v=Ui.title(value,23);v.setForeground(Ui.isDark()?(accent.equals(Ui.TEAL)?new Color(0x62D4C0):new Color(0xF1C16B)):accent);c.add(v);JLabel n=Ui.hint(note);n.setFont(n.getFont().deriveFont(11f));c.add(n,BorderLayout.SOUTH);return c;}
    private <T> T selected(JTable table,List<T> values){int row=table.getSelectedRow();if(row<0 || values.isEmpty())throw new IllegalArgumentException("Selecione um registro na tabela.");return values.get(table.convertRowIndexToModel(row));}
    private <T> T selectedOrFirst(JTable table,List<T> values){return table.getSelectedRow()<0?values.get(0):selected(table,values);}
    private static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
    private static boolean matches(String query,String... values){String q=normalized(query.strip());return q.isEmpty() || Arrays.stream(values).anyMatch(v->normalized(v).contains(q));}
    private JComboBox<String> categoryFilter(){TreeSet<String> cats=new TreeSet<>();materials.stream().map(Material::category).filter(s->!s.isBlank()).forEach(cats::add);JComboBox<String> f=new JComboBox<>();f.addItem("Todas as categorias");cats.forEach(f::addItem);return f;}
    private JPanel actions(JComponent...components){JPanel p=Ui.panel(new FlowLayout(FlowLayout.LEFT,8,0));for(JComponent c:components)p.add(c);return p;}
    private JPanel materialsPage(){
        JPanel root=Ui.card();JTextField search=Ui.input("");currentSearch=search;search.setPreferredSize(new Dimension(280,38));Ui.placeholder(search,"Buscar código, nome ou código de barras…");
        JComboBox<String> cat=categoryFilter(),status=new JComboBox<>(new String[]{"Ativos","Todos","Estoque baixo","Sem estoque","Arquivados"});
        JLabel count=Ui.hint("");JPanel filters=Ui.panel(new BorderLayout(10,0));filters.add(search);filters.add(actions(cat,status),BorderLayout.EAST);
        JTable table=Ui.table("Código","Material","Categoria","Saldo","Mínimo","Custo médio","Situação","Localização");
        int[] widths={100,240,110,100,70,115,135,100};for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        Ui.numericSort(table,3,4,5);
        List<Material> visible=new ArrayList<>();
        Runnable filter=()->{
            visible.clear();for(Material m:materials){
                String chosen=Objects.toString(status.getSelectedItem());
                boolean good=switch(chosen){case "Todos"->true;case "Arquivados"->!m.active();case "Estoque baixo"->m.active() && m.stock()>0 && m.stock()<=m.minimum();case "Sem estoque"->m.active() && m.stock()==0;default->m.active();};
                if(good && (cat.getSelectedIndex()==0 || Objects.equals(cat.getSelectedItem(),m.category())) && matches(search.getText(),m.sku(),m.name(),m.barcode(),m.location(),m.category()))visible.add(m);
            }
            Ui.rows(table,visible.stream().map(m->new Object[]{m.sku(),m.name(),m.category(),Numbers.qty(m.stock())+" "+m.unit(),Numbers.qty(m.minimum()),Numbers.currency(m.costCents()),m.status(),m.location()}).toList());count.setText(visible.size()+" materiais encontrados · duplo clique para editar");
        };
        Ui.watch(search,filter);cat.addActionListener(e->filter.run());status.addActionListener(e->filter.run());filter.run();
        JButton more=Ui.button("Mais ações ▾",()->{});JPopupMenu menu=new JPopupMenu();
        menuItem(menu,"Exportar materiais filtrados (CSV)",()->attempt(()->{Path p=saveFile("materiais.csv","csv");if(p!=null){Csv.write(p,Reports.materialHeaders(),Reports.materialRows(visible));toast.setText("CSV exportado: "+p);}}));
        menuItem(menu,"Baixar modelo de importação",()->attempt(()->{Path p=saveFile("modelo-materiais.csv","csv");if(p!=null){Csv.template(p);toast.setText("Modelo CSV salvo: "+p);}}));
        menuItem(menu,"Importar novos materiais (CSV)",this::importCsv);menu.addSeparator();
        menuItem(menu,"Arquivar / reativar selecionado",()->attempt(()->{Material m=selected(table,visible);if(confirm("Alterar situação",(m.active()?"Arquivar ":"Reativar ")+m.name()+"? O histórico será preservado.")){service.setMaterialActive(m.id(),!m.active());reload("Situação atualizada.");}}));
        more.addActionListener(e->menu.show(more,0,more.getHeight()));
        JPanel top=Ui.panel(new BorderLayout(0,14));top.add(filters,BorderLayout.NORTH);top.add(actions(Ui.button("Editar",()->attempt(()->openMaterial(selected(table,visible)))),Ui.primary("Movimentar estoque",()->attempt(()->openMovement(selected(table,visible)))),more));
        root.add(top,BorderLayout.NORTH);root.add(new JScrollPane(table));root.add(count,BorderLayout.SOUTH);Ui.doubleClick(table,()->attempt(()->openMaterial(selected(table,visible))));return root;
    }
    private void menuItem(JPopupMenu menu,String title,Runnable action){JMenuItem item=new JMenuItem(title);item.addActionListener(e->action.run());menu.add(item);}
    private void importCsv(){attempt(()->{
        Path file=openFile("Selecionar CSV de novos materiais","csv");if(file==null)return;
        Csv.ImportBatch batch=Csv.readImport(file);
        String sample=batch.materials().stream().limit(5).map(m->m.sku()+" • "+m.name()).reduce("",(a,b)->a+b+"\n");
        if(!confirm("Prévia da importação",batch.materials().size()+" novos materiais:\n\n"+sample+"\nImportar todos? Códigos existentes serão rejeitados; nenhum registro será alterado em caso de erro."))return;
        work("Importando materiais",()->service.importMaterials(batch.materials(),batch.quantities(),settings.operator()),()->reload("Importados "+batch.materials().size()+" materiais."));
    });}
    private JPanel movementsPage(){
        JPanel root=Ui.card();JTextField search=Ui.input("");currentSearch=search;Ui.placeholder(search,"Buscar material, motivo, documento ou responsável…");
        JComboBox<String> type=new JComboBox<>(new String[]{"Todas as operações","Entrada","Saída","Ajuste"});JLabel count=Ui.hint("");
        JTable table=Ui.table("Data / hora","Código","Material","Operação","Quantidade","Saldo após","Responsável","Motivo","Documento");table.getColumnModel().getColumn(2).setPreferredWidth(210);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==2 || i==7?230:140);
        List<Movement> visible=new ArrayList<>();Runnable filter=()->{visible.clear();movements.stream().filter(m->type.getSelectedIndex()==0 || m.typeLabel().equals(type.getSelectedItem())).filter(m->matches(search.getText(),m.sku(),m.name(),m.reason(),m.reference(),m.operator())).forEach(visible::add);
            Ui.rows(table,visible.stream().map(m->new Object[]{Reports.date(m.createdAt()),m.sku(),m.name(),m.typeLabel(),Numbers.qty(m.delta())+" "+m.unit(),Numbers.qty(m.balance()),m.operator(),m.reason(),m.reference()}).toList());count.setText(visible.size()+" movimentações · histórico permanente · ajuste gera novo lançamento");};
        Ui.watch(search,filter);type.addActionListener(e->filter.run());filter.run();
        Ui.numericSort(table,4,5);Ui.dateSort(table,0);
        JPanel top=Ui.panel(new BorderLayout(12,14));top.add(search);top.add(type,BorderLayout.EAST);top.add(actions(Ui.primary("+ Registrar movimentação",()->openMovement(null)),Ui.button("Exportar CSV",()->attempt(()->{Path p=saveFile("movimentacoes.csv","csv");if(p!=null){Csv.write(p,Reports.movementHeaders(),Reports.movementRows(visible));toast.setText("Histórico exportado.");}}))),BorderLayout.SOUTH);
        root.add(top,BorderLayout.NORTH);root.add(new JScrollPane(table));root.add(count,BorderLayout.SOUTH);return root;
    }
    private JPanel suppliersPage(){
        JPanel root=Ui.card();JTextField search=Ui.input("");currentSearch=search;Ui.placeholder(search,"Buscar nome, documento ou contato…");JCheckBox archived=new JCheckBox("Incluir arquivados");
        JTable table=Ui.table("Fornecedor","Documento","Contato","Telefone","E-mail","Situação");List<Supplier> visible=new ArrayList<>();JLabel count=Ui.hint("");
        Runnable filter=()->{visible.clear();suppliers.stream().filter(s->archived.isSelected() || s.active()).filter(s->matches(search.getText(),s.name(),s.document(),s.contact(),s.phone(),s.email())).forEach(visible::add);Ui.rows(table,visible.stream().map(s->new Object[]{s.name(),s.document(),s.contact(),s.phone(),s.email(),s.active()?"Ativo":"Arquivado"}).toList());count.setText(visible.size()+" fornecedores encontrados");};
        Ui.watch(search,filter);archived.addActionListener(e->filter.run());filter.run();
        JPanel top=Ui.panel(new BorderLayout(12,14));top.add(search);top.add(archived,BorderLayout.EAST);top.add(actions(Ui.button("Editar",()->attempt(()->openSupplier(selected(table,visible)))),Ui.button("Arquivar / reativar",()->attempt(()->{Supplier s=selected(table,visible);if(confirm("Alterar fornecedor",(s.active()?"Arquivar ":"Reativar ")+s.name()+"?")){service.setSupplierActive(s.id(),!s.active());reload("Fornecedor atualizado.");}})),Ui.button("Exportar CSV",()->attempt(()->{Path p=saveFile("fornecedores.csv","csv");if(p!=null)Csv.write(p,List.of("Fornecedor","Documento","Contato","Telefone","Email","Endereço","Observações","Situação"),visible.stream().map(s->List.of(s.name(),s.document(),s.contact(),s.phone(),s.email(),s.address(),s.notes(),s.active()?"Ativo":"Arquivado")).toList());}))),BorderLayout.SOUTH);
        root.add(top,BorderLayout.NORTH);root.add(new JScrollPane(table));root.add(count,BorderLayout.SOUTH);Ui.doubleClick(table,()->attempt(()->openSupplier(selected(table,visible))));return root;
    }
    private JPanel reportsPage(){
        JPanel root=Ui.card();JComboBox<String> type=new JComboBox<>(new String[]{"Inventário atual","Reposição (saldo ≤ mínimo)","Movimentações por período"});
        JTextField from=Ui.input(LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),to=Ui.input(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));from.setColumns(8);to.setColumns(8);
        JTable table=Ui.table("Código","Material","Saldo","Valor","Situação");List<Material> selectedM=new ArrayList<>();List<Movement> selectedV=new ArrayList<>();JLabel count=Ui.hint("");
        boolean[] valid={false};Runnable filter=()->{valid[0]=false;attempt(()->{
            boolean history=type.getSelectedIndex()==2;selectedM.clear();selectedV.clear();
            if(history){LocalDate first=parseDate(from.getText()),last=parseDate(to.getText());if(first.isAfter(last))throw new IllegalArgumentException("A data inicial deve ser anterior ou igual à final.");
                movements.stream().filter(m->{LocalDate d=LocalDateTime.parse(m.createdAt()).toLocalDate();return !d.isBefore(first) && !d.isAfter(last);}).forEach(selectedV::add);
                ((DefaultTableModel)table.getModel()).setColumnIdentifiers(new String[]{"Data / hora","Material","Operação","Quantidade","Responsável"});
                Ui.rows(table,selectedV.stream().map(m->new Object[]{Reports.date(m.createdAt()),m.name(),m.typeLabel(),Numbers.qty(m.delta())+" "+m.unit(),m.operator()}).toList());count.setText(selectedV.size()+" movimentações no período");
            }else{materials.stream().filter(Material::active).filter(m->type.getSelectedIndex()==0 || m.stock()<=m.minimum()).forEach(selectedM::add);
                ((DefaultTableModel)table.getModel()).setColumnIdentifiers(new String[]{"Código","Material","Saldo","Valor em estoque","Situação"});
                Ui.rows(table,selectedM.stream().map(m->new Object[]{m.sku(),m.name(),Numbers.qty(m.stock())+" "+m.unit(),Numbers.currency(Numbers.valuation(m.stock(),m.costCents())),m.status()}).toList());count.setText(selectedM.size()+" materiais ativos · saldos atuais");}
            Ui.numericSort(table,history?3:2);if(history)Ui.dateSort(table,0);else Ui.numericSort(table,3);valid[0]=true;
        });};
        Runnable update=()->{boolean h=type.getSelectedIndex()==2;from.setEnabled(h);to.setEnabled(h);filter.run();};type.addActionListener(e->update.run());update.run();
        JPanel top=Ui.panel(new BorderLayout(0,12));top.add(actions(type,new JLabel("De"),from,new JLabel("Até"),to,Ui.button("Aplicar",filter)),BorderLayout.NORTH);
        top.add(actions(Ui.primary("Exportar CSV",()->{filter.run();if(!valid[0])return;attempt(()->{Path p=saveFile(type.getSelectedIndex()==2?"relatorio-movimentacoes.csv":"relatorio-inventario.csv","csv");if(p!=null){Csv.write(p,type.getSelectedIndex()==2?Reports.movementHeaders():Reports.materialHeaders(),type.getSelectedIndex()==2?Reports.movementRows(selectedV):Reports.materialRows(selectedM));toast.setText("Relatório CSV exportado.");}});}),Ui.button("Relatório para impressão (HTML)",()->{filter.run();if(!valid[0])return;attempt(()->{Path p=saveFile("relatorio-materia.html","html");if(p!=null){Reports.html(p,settings.company(),selectedM,selectedV,type.getSelectedIndex()==2);toast.setText("Relatório salvo. Abra no navegador e use Ctrl+P para imprimir ou salvar em PDF.");}});}),Ui.button("Imprimir tabela",()->{filter.run();if(valid[0])attempt(()->table.print(JTable.PrintMode.FIT_WIDTH));})),BorderLayout.SOUTH);
        root.add(top,BorderLayout.NORTH);root.add(new JScrollPane(table));root.add(count,BorderLayout.SOUTH);return root;
    }
    private static LocalDate parseDate(String input){try{return LocalDate.parse(input.strip(),DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));}catch(DateTimeParseException e){throw new IllegalArgumentException("Data inválida. Use dd/mm/aaaa (ex.: 02/11/2026).");}}
    private JPanel settingsPage(){
        JPanel body=Ui.panel(new BorderLayout(0,18));JPanel grid=Ui.panel(new GridLayout(1,2,18,0));
        JPanel prefs=Ui.card();prefs.add(Ui.title("Seu ambiente",18),BorderLayout.NORTH);Ui.Form form=new Ui.Form();JTextField company=Ui.input(settings.company()),operator=Ui.input(settings.operator());JCheckBox dark=new JCheckBox("Usar tema escuro",settings.dark());
        form.full("Empresa / almoxarifado",company);form.full("Responsável padrão",operator);form.full("Aparência",dark);form.full("Atalhos",Ui.hint("<html>Ctrl+N · Novo material<br>Ctrl+M · Movimentar estoque<br>Ctrl+F · Buscar na tela<br>F5 · Atualizar dados</html>"));form.finish();JScrollPane prefScroll=new JScrollPane(form);prefScroll.setBorder(null);prefScroll.getVerticalScrollBar().setUnitIncrement(16);prefs.add(prefScroll);
        prefs.add(Ui.primary("Salvar preferências",()->attempt(()->{settings.save(operator.getText(),company.getText(),dark.isSelected());Ui.theme(settings.dark());SwingUtilities.updateComponentTreeUI(this);reload("Preferências salvas.");})),BorderLayout.SOUTH);grid.add(prefs);
        JPanel backups=Ui.card();backups.add(Ui.title("Proteção dos dados",18),BorderLayout.NORTH);JPanel buttons=Ui.panel(new GridLayout(0,1,0,12));
        buttons.add(Ui.hint("<html>Backup automático ao abrir o aplicativo.<br>Até 14 cópias diárias no computador.<br>Guarde também uma cópia em outro dispositivo.</html>"));
        buttons.add(Ui.button("Criar backup agora",()->attempt(()->{Path p=saveFile("materia-backup-"+LocalDate.now()+".db","db");if(p!=null)work("Criando backup",()->service.database().backup(p),()->toast.setText("Backup criado: "+p));})));
        buttons.add(Ui.button("Restaurar backup…",()->attempt(()->{Path p=openFile("Restaurar backup do Materia","db");if(p!=null && confirm("Restaurar backup","Substituir os dados atuais pelo backup selecionado?\nUma cópia dos dados atuais será preservada antes da troca."))work("Restaurando backup",()->service.database().restore(p),()->reload("Backup restaurado. Cópia anterior preservada na pasta backups."));})));
        buttons.add(Ui.button("Abrir pasta dos dados",()->attempt(()->Desktop.getDesktop().open(service.database().directory().toFile()))));
        buttons.add(Ui.button("Carregar demonstração",()->attempt(()->{if(confirm("Dados fictícios","Adicionar materiais fictícios para conhecer o sistema?\nEssa opção exige uma base sem materiais.")){service.demo();reload("Demonstração carregada: dados fictícios.");}})));
        backups.add(buttons,BorderLayout.CENTER);backups.add(Ui.hint("<html>Banco: materia.db<br>O nome do responsável é um registro informativo;<br>este aplicativo local não possui autenticação.</html>"),BorderLayout.SOUTH);grid.add(backups);body.add(grid);
        JPanel about=Ui.card();about.add(Ui.title("Materia 1.0.0  /  Java 17 + Swing + SQLite",16),BorderLayout.NORTH);about.add(Ui.hint("<html>Aplicativo local para Windows x64. Um usuário por base. Não sincroniza entre computadores.<br>Relatórios financeiros refletem o custo médio do estoque; não substituem um sistema contábil.</html>"));body.add(about,BorderLayout.SOUTH);return body;
    }
    private Path saveFile(String name,String extension){
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Salvar arquivo");chooser.setSelectedFile(new java.io.File(name));chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(extension.toUpperCase()+" (*."+extension+")",extension));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return null;
        Path p=chooser.getSelectedFile().toPath().toAbsolutePath();if(!p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("."+extension))p=p.resolveSibling(p.getFileName()+"."+extension);
        if(p.normalize().equals(service.database().file().normalize()))throw new IllegalArgumentException("Não sobrescreva o banco em uso.");
        if(extension.equals("db") && Files.exists(p))throw new IllegalArgumentException("Para backups, escolha um nome novo.");
        if(Files.exists(p) && !confirm("Substituir arquivo","O arquivo já existe. Deseja substituir?"))return null;return p;
    }
    private Path openFile(String title,String extension){JFileChooser chooser=new JFileChooser();chooser.setDialogTitle(title);chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(extension.toUpperCase(),extension));return chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION?chooser.getSelectedFile().toPath().toAbsolutePath():null;}
}
