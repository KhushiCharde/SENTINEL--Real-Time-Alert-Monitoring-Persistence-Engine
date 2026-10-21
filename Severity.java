package sentinel;

public enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL;
    public static Severity forCount(int count, String level) {
        if (level.equals("FATAL") || count >= 10) return CRITICAL;
        if (count >= 5) return HIGH;
        if (count >= 3) return MEDIUM;
        return LOW;
    }
}
