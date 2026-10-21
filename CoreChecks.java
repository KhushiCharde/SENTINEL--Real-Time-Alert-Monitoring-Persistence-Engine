package sentinel;

import java.nio.file.*;
import java.sql.*;
import java.time.*;

/** Dependency-free checks. Throw AssertionError on failure (no -ea flag needed). */
public final class CoreChecks {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-20T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    static LogEvent event(String server, String level, String message) {
        return LogEvent.parse("2026-10-20T10:00:00Z|"+server+"|database|"+level+"|"+message);
    }
    public static void main(String[] args) throws Exception {
        TestClock clock = new TestClock();
        AlertEngine engine = new AlertEngine(Duration.ofSeconds(60), clock);
        LogEvent e = event("web-01", "ERROR", "Database pool exhausted");
        int emitted = 0;
        Severity[] expected = {Severity.LOW,Severity.LOW,Severity.MEDIUM,Severity.MEDIUM,
            Severity.HIGH,Severity.HIGH,Severity.HIGH,Severity.HIGH,Severity.HIGH,Severity.CRITICAL};
        Path db = Files.createTempFile("sentinel-check-", ".db");
        try {
            try (HistoryStore store = new HistoryStore(db)) {
                for (int n = 0; n < 10; n++) {
                    AlertEngine.Change c = engine.accept(e);
                    check(c.alert().severity() == expected[n], "Wrong threshold at " + (n+1));
                    if (c.emit()) emitted++;
                    store.save(e,c);
                }
                check(emitted == 4, "10 inputs must emit 4 notifications (60% suppression)");
                store.save(event("web-01","INFO","Started"),null);
            }
            try (HistoryStore reopened = new HistoryStore(db);
                 Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
                 Statement s = c.createStatement()) {
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM events")) {
                    r.next(); check(r.getInt(1) == 11,"Persistence across reconnect");
                }
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM alert_transitions")) {
                    r.next(); check(r.getInt(1) == 4,"Transitions must be stored");
                }
            }
            check(engine.accept(event("web-02","ERROR",e.message())).alert().count()==1,"Servers must be separate");
            check(engine.accept(event("web-01","WARN",e.message())).alert().count()==1,"Levels must be separate");
            check(engine.accept(event("web-01","FATAL","Failure")).alert().severity()==Severity.CRITICAL,"Fatal is immediate");
            clock.now = clock.now.plusSeconds(60);
            check(engine.snapshot().isEmpty(), "Expired alerts must disappear at exact boundary");
            check(engine.accept(e).alert().count()==1,"Next window resets count");
            check(LogEvent.parse("2026-10-20T10:00:00Z|s|c|ERROR|a|b").message().equals("a|b"),"Pipes in messages");
            check(LogEvent.parse("2026-10-20T10:00:00Z|s|c|warn|x").level().equals("WARN"),"Level normalization");
            for(String bad: new String[]{"invalid", "x|s|c|ERROR|msg", "2026-10-20T10:00:00Z|s|c|DEBUG|msg", "2026-10-20T10:00:00Z||c|ERROR|msg"}) {
                boolean rejected=false;
                try { LogEvent.parse(bad); } catch(RuntimeException ex) { rejected=true; }
                check(rejected,"Invalid input accepted: " + bad);
            }
            check(Dashboard.safe("x\033[31m").equals("x?[31m"),"Terminal escape sanitization");
            System.out.println("PASS: thresholds, dedup isolation, expiry, persistence, transition history, parser, terminal sanitization");
        } finally { Files.deleteIfExists(db); }
    }
}
