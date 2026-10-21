package sentinel;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.io.*;

public final class Main {
    private final AlertEngine engine;
    private final HistoryStore store;
    private long valid, alerts, emitted, invalid;
    private Main(HistoryStore store, long window) {
        this.store = store; this.engine = new AlertEngine(Duration.ofSeconds(window), Clock.systemUTC());
    }
    private void process(String line) {
        if (line.isBlank() || line.startsWith("#")) return;
        LogEvent event;
        try {
            if (line.length() > 65536) throw new IllegalArgumentException("Line exceeds 64 KiB");
            event = LogEvent.parse(line);
        } catch (RuntimeException e) {
            invalid++; System.err.println("Skipped malformed log: " + Dashboard.safe(e.getMessage())); return;
        }
        AlertEngine.Change change = event.isAlert() ? engine.accept(event) : null;
        try { store.save(event, change); }
        catch (Exception e) { throw new IllegalStateException("Database write failed; monitoring stopped to prevent silent history loss", e); }
        valid++;
        if (change != null) {
            alerts++;
            if (change.emit()) {
                emitted++;
                System.out.printf("ALERT [%s] %s | %s | count=%d | %s%n", change.alert().severity(),
                        Dashboard.safe(event.server()), event.category(), change.alert().count(), Dashboard.safe(event.message()));
            }
        }
    }
    private void dashboard() { Dashboard.render(engine, valid, alerts, emitted, invalid); }
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception e) { System.err.println("SENTINEL: " + e.getMessage()); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        String command = args.length == 0 ? "demo" : args[0];
        if (command.equals("help") || command.equals("--help")) {
            System.out.println("SENTINEL: demo | read FILE | watch FILE [--from-end] | history");
            System.out.println("Options: --db PATH (default data/sentinel.db), --window SECONDS (default 60)"); return;
        }
        if (!Set.of("demo", "read", "watch", "history").contains(command)) throw new IllegalArgumentException("Unknown command; use help");
        Path input = null, db = Path.of("data/sentinel.db");
        long window = 60; boolean fromEnd = false;
        int i = 1;
        if (command.equals("read") || command.equals("watch")) {
            if (args.length < 2 || args[1].startsWith("--")) throw new IllegalArgumentException("Provide an input log file");
            input = Path.of(args[i++]);
        }
        for (; i < args.length; i++) {
            switch (args[i]) {
                case "--db" -> { if (++i == args.length) throw new IllegalArgumentException("Missing database path"); db = Path.of(args[i]); }
                case "--window" -> { if (++i == args.length) throw new IllegalArgumentException("Missing window"); window = Long.parseLong(args[i]); }
                case "--from-end" -> fromEnd = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (window < 1 || window > 86400) throw new IllegalArgumentException("Window must be between 1 and 86400 seconds");
        if (fromEnd && !command.equals("watch")) throw new IllegalArgumentException("--from-end requires watch");
        try (HistoryStore history = new HistoryStore(db)) {
            Main app = new Main(history, window);
            switch (command) {
                case "demo" -> {
                    System.out.println("SENTINEL demo: 10 identical synthetic errors, 4 alert notifications.");
                    for (int n = 1; n <= 10; n++) app.process(Instant.now() + "|web-01|database|ERROR|Database connection pool exhausted");
                    app.dashboard();
                }
                case "history" -> history.printHistory();
                case "read" -> {
                    try (BufferedReader reader = Files.newBufferedReader(input)) {
                        String line; while ((line = reader.readLine()) != null) app.process(line);
                    }
                    app.dashboard();
                }
                case "watch" -> {
                    System.out.println("Watching " + input.toAbsolutePath() + " (Ctrl+C to stop; dashboard every 5s)");
                    app.dashboard(); FileTailer.follow(input, fromEnd, app::process, app::dashboard);
                }
                default -> throw new IllegalStateException("Unknown command");
            }
        }
    }
}
