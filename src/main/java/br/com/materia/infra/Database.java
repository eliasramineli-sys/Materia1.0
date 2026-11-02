package br.com.materia.infra;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

public final class Database {
    private final Path directory;
    private final Path file;
    public Database(Path directory) throws Exception {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        file = this.directory.resolve("materia.db");
        Class.forName("org.sqlite.JDBC");
        initialize();
    }
    public Path directory() { return directory; }
    public Path file() { return file; }
    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }
    private void initialize() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("PRAGMA user_version")) {
                if (r.next() && r.getInt(1) > 1) throw new SQLException("Banco criado por versão mais recente. Atualize o Materia.");
            }
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS suppliers (
                    id INTEGER PRIMARY KEY, name TEXT NOT NULL, document TEXT NOT NULL DEFAULT '',
                    contact TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '',
                    address TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)))
                """);
            s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS supplier_document ON suppliers(document) WHERE document<>''");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS materials (
                    id INTEGER PRIMARY KEY, sku TEXT NOT NULL COLLATE NOCASE UNIQUE, name TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT '', unit TEXT NOT NULL, location TEXT NOT NULL DEFAULT '',
                    supplier_id INTEGER REFERENCES suppliers(id), barcode TEXT NOT NULL DEFAULT '',
                    cost_cents INTEGER NOT NULL CHECK(cost_cents>=0), price_cents INTEGER NOT NULL CHECK(price_cents>=0),
                    minimum_milli INTEGER NOT NULL CHECK(minimum_milli>=0), stock_milli INTEGER NOT NULL CHECK(stock_milli>=0),
                    notes TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
                    created_at TEXT NOT NULL, updated_at TEXT NOT NULL)
                """);
            s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS material_barcode ON materials(barcode) WHERE barcode<>''");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS movements (
                    id INTEGER PRIMARY KEY, material_id INTEGER NOT NULL REFERENCES materials(id),
                    type TEXT NOT NULL CHECK(type IN ('ENTRY','EXIT','ADJUST')), delta_milli INTEGER NOT NULL CHECK(delta_milli<>0),
                    balance_milli INTEGER NOT NULL CHECK(balance_milli>=0), cost_cents INTEGER NOT NULL CHECK(cost_cents>=0),
                    reason TEXT NOT NULL, reference TEXT NOT NULL DEFAULT '', operator TEXT NOT NULL, created_at TEXT NOT NULL)
                """);
            s.executeUpdate("CREATE INDEX IF NOT EXISTS movement_material ON movements(material_id,id)");
            s.executeUpdate("PRAGMA user_version=1");
        }
    }
    public void backup(Path destination) throws Exception {
        Path dest = destination.toAbsolutePath().normalize();
        if (dest.equals(file)) throw new IllegalArgumentException("Escolha outro arquivo para o backup.");
        if (Files.exists(dest)) throw new IllegalArgumentException("O arquivo já existe. Escolha um nome novo.");
        if (dest.getParent()!=null) Files.createDirectories(dest.getParent());
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement("VACUUM INTO ?")) {
            p.setString(1, dest.toString()); p.execute();
        }
    }
    public void dailyBackup() throws Exception {
        Path folder = directory.resolve("backups");
        Files.createDirectories(folder);
        Path target = folder.resolve("materia-" + LocalDate.now() + ".db");
        if (!Files.exists(target)) backup(target);
        try (var stream = Files.list(folder)) {
            var old = stream.filter(p -> p.getFileName().toString().matches("materia-\\d{4}-\\d{2}-\\d{2}\\.db"))
                    .sorted(Comparator.reverseOrder()).skip(14).toList();
            for (Path p : old) Files.deleteIfExists(p);
        }
    }
    public void restore(Path source) throws Exception {
        Path candidate = directory.resolve("restore-candidate.db");
        if (source.toAbsolutePath().normalize().equals(file)) throw new IllegalArgumentException("Esse já é o banco em uso.");
        if (source.toAbsolutePath().normalize().equals(candidate)) throw new IllegalArgumentException("Arquivo reservado pelo sistema.");
        Files.copy(source, candidate, StandardCopyOption.REPLACE_EXISTING);
        try {
            validateBackup(candidate);
            Path safety = directory.resolve("backups/pre-restauracao-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".db");
            backup(safety);
            try { Files.move(candidate, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(candidate, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(candidate); }
    }
    private static void validateBackup(Path candidate) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:file:" + candidate.toUri().getRawPath() + "?mode=ro"); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("PRAGMA integrity_check")) {
                if (!r.next() || !"ok".equals(r.getString(1))) throw new IOException("Backup corrompido.");
            }
            try (ResultSet r = s.executeQuery("PRAGMA user_version")) {
                if (!r.next() || r.getInt(1)!=1) throw new IOException("Backup incompatível com o Materia 1.0.");
            }
            s.executeQuery("SELECT id,sku,name,category,unit,location,supplier_id,barcode,cost_cents,price_cents,minimum_milli,stock_milli,notes,active,created_at,updated_at FROM materials LIMIT 0").close();
            s.executeQuery("SELECT id,name,document,contact,phone,email,address,notes,active FROM suppliers LIMIT 0").close();
            s.executeQuery("SELECT id,material_id,type,delta_milli,balance_milli,cost_cents,reason,reference,operator,created_at FROM movements LIMIT 0").close();
            try (ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) { if (r.next()) throw new IOException("Backup com vínculos inválidos."); }
            try (ResultSet r = s.executeQuery("SELECT m.id FROM materials m LEFT JOIN movements v ON v.material_id=m.id GROUP BY m.id HAVING m.stock_milli<>COALESCE(SUM(v.delta_milli),0)")) {
                if (r.next()) throw new IOException("Backup com saldo de estoque inconsistente.");
            }
        } catch (SQLException ex) { throw new IOException("O arquivo não é um backup válido do Materia.", ex); }
    }
}
