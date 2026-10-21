package sentinel;

import java.time.*;
import java.util.*;

/** Single-threaded HashMap aggregation; a fixed processing-time window per signature. */
public final class AlertEngine {
    public record Key(String server, String component, String level, String message) {}
    public record Alert(Key key, String category, int count, Severity severity,
                        Instant firstSeen, Instant lastSeen) {}
    public record Change(Alert alert, Severity previous, boolean emit) {}
    private final HashMap<Key, Alert> alerts = new HashMap<>();
    private final Duration window;
    private final Clock clock;
    public AlertEngine(Duration window, Clock clock) {
        if (window.isZero() || window.isNegative()) throw new IllegalArgumentException("Window must be positive");
        this.window = window; this.clock = clock;
    }
    public void expire() {
        Instant now = clock.instant();
        alerts.values().removeIf(a -> !now.isBefore(a.firstSeen().plus(window)));
    }
    public Change accept(LogEvent event) {
        if (!event.isAlert()) throw new IllegalArgumentException("INFO is not an alert");
        expire();
        Key key = new Key(event.server(), event.component(), event.level(), event.message());
        Alert old = alerts.get(key);
        if (old == null && alerts.size() >= 10000)
            throw new IllegalStateException("10,000 active signatures reached; shorten the window or filter input");
        int count = old == null ? 1 : old.count() + 1;
        Severity severity = Severity.forCount(count, event.level());
        Instant now = clock.instant();
        Alert next = new Alert(key, event.category(), count, severity,
                old == null ? now : old.firstSeen(), now);
        alerts.put(key, next);
        return new Change(next, old == null ? null : old.severity(),
                old == null || old.severity() != severity);
    }
    public List<Alert> snapshot() {
        expire();
        return alerts.values().stream()
            .sorted(Comparator.comparing(Alert::severity).reversed()
            .thenComparing(a -> a.key().server()).thenComparing(a -> a.key().message())).toList();
    }
}
