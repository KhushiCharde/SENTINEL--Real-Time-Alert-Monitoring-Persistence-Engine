package sentinel;

import java.time.Instant;
import java.util.Locale;

/** Input: ISO-8601 timestamp|server|component|INFO/WARN/ERROR/FATAL|message */
public record LogEvent(Instant timestamp, String server, String component, String level, String message) {
    public static LogEvent parse(String line) {
        String[] fields = line.split("\\|", 5);
        if (fields.length != 5) throw new IllegalArgumentException("Expected five pipe-separated fields");
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].trim();
            if (fields[i].isEmpty()) throw new IllegalArgumentException("Fields cannot be empty");
        }
        String level = fields[3].toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("INFO", "WARN", "ERROR", "FATAL").contains(level))
            throw new IllegalArgumentException("Unsupported level: " + level);
        return new LogEvent(Instant.parse(fields[0]), fields[1], fields[2], level, fields[4]);
    }
    public boolean isAlert() { return !level.equals("INFO"); }
    public String category() {
        String text = (component + " " + message).toLowerCase(Locale.ROOT);
        if (text.matches(".*(database|jdbc|sql|connection pool).*")) return "DATABASE";
        if (text.matches(".*(memory|heap|outofmemory).*")) return "MEMORY";
        if (text.matches(".*(disk|storage|filesystem).*")) return "DISK";
        if (text.matches(".*(cpu|processor).*")) return "CPU";
        if (text.matches(".*(network|socket|dns|timeout).*")) return "NETWORK";
        return "APPLICATION";
    }
}
