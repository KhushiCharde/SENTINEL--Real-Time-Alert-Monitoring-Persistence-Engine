package sentinel;

import java.nio.file.*;
import java.sql.*;

/** Every event is retained, even when its console notification is suppressed. */
public final class HistoryStore implements AutoCloseable {
    private final Connection connection;
    public HistoryStore(Path path) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY, log_time TEXT NOT NULL, received_at TEXT NOT NULL, server TEXT NOT NULL, component TEXT NOT NULL, level TEXT NOT NULL, category TEXT NOT NULL, message TEXT NOT NULL, severity TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS alert_transitions (id INTEGER PRIMARY KEY, event_id INTEGER NOT NULL REFERENCES events(id), previous_severity TEXT, new_severity TEXT NOT NULL, occurrence_count INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_events_server_time ON events(server, log_time)");
            s.execute("PRAGMA foreign_keys=ON");
        }
    }
    public void save(LogEvent e, AlertEngine.Change change) throws SQLException {
        connection.setAutoCommit(false);
        try {
            long id;
            try (PreparedStatement s = connection.prepareStatement(
                    "INSERT INTO events(log_time,received_at,server,component,level,category,message,severity) VALUES(?,?,?,?,?,?,?,?)")) {
                s.setString(1, e.timestamp().toString());
                s.setString(2, java.time.Instant.now().toString());
                s.setString(3, e.server()); s.setString(4, e.component());
                s.setString(5, e.level()); s.setString(6, e.category()); s.setString(7, e.message());
                s.setString(8, change == null ? null : change.alert().severity().name());
                s.executeUpdate();
            }
            try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) {
                r.next(); id = r.getLong(1);
            }
            if (change != null && change.emit()) {
                try (PreparedStatement s = connection.prepareStatement(
                        "INSERT INTO alert_transitions(event_id,previous_severity,new_severity,occurrence_count) VALUES(?,?,?,?)")) {
                    s.setLong(1, id);
                    s.setString(2, change.previous() == null ? null : change.previous().name());
                    s.setString(3, change.alert().severity().name()); s.setInt(4, change.alert().count());
                    s.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e1) {
            connection.rollback(); throw e1;
        } finally { connection.setAutoCommit(true); }
    }
    public void printHistory() throws SQLException {
        System.out.println("Recent persisted events (latest 20):");
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(
                "SELECT id,log_time,server,category,level,COALESCE(severity,'-') AS severity,message FROM events ORDER BY id DESC LIMIT 20")) {
            while (r.next()) System.out.printf("#%d %s %-12s %-11s %-5s %-8s %s%n",
                    r.getLong("id"), r.getString("log_time"), Dashboard.safe(r.getString("server")),
                    r.getString("category"), r.getString("level"), r.getString("severity"), Dashboard.safe(r.getString("message")));
        }
    }
    @Override public void close() throws SQLException { connection.close(); }
}
