package sentinel;

import java.util.*;

public final class Dashboard {
    private Dashboard() {}
    public static String safe(String value) {
        // Log text cannot inject terminal escape/control sequences.
        return value.replaceAll("[\\p{Cntrl}]", "?");
    }
    public static void render(AlertEngine engine, long valid, long errors, long emitted, long invalid) {
        List<AlertEngine.Alert> active = engine.snapshot();
        Severity overall = active.stream().map(AlertEngine.Alert::severity)
                .max(Comparator.naturalOrder()).orElse(null);
        System.out.println("\n================ SENTINEL | SERVER HEALTH ================");
        System.out.println("Observed alert state: " + (overall == null ? "NO ACTIVE ALERTS" : overall));
        System.out.printf("Events: %d | Alert events: %d | Emitted: %d | Suppressed: %d | Invalid: %d%n",
                valid, errors, emitted, errors - emitted, invalid);
        System.out.printf(Locale.ROOT, "Alert-notification reduction: %.1f%%%n", errors == 0 ? 0 : 100.0 * (errors-emitted)/errors);
        System.out.printf("%-12s %-11s %-9s %7s  %s%n", "SERVER", "CATEGORY", "SEVERITY", "COUNT", "MESSAGE");
        active.stream().limit(20).forEach(a -> System.out.printf("%-12.12s %-11s %-9s %7d  %.65s%n",
                safe(a.key().server()), a.category(), a.severity(), a.count(), safe(a.key().message())));
        if (active.size() > 20) System.out.println("Showing 20 of " + active.size() + " active signatures.");
        System.out.println("==========================================================");
    }
}
