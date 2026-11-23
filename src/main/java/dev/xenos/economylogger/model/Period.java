package dev.xenos.economylogger.model;

public enum Period {
    DAY(86_400_000L), WEEK(7 * 86_400_000L), MONTH(30 * 86_400_000L), ALL(0);

    private final long millis;

    Period(long millis) { this.millis = millis; }

    public long since() { return millis == 0 ? 0 : System.currentTimeMillis() - millis; }

    public Period next() { return values()[(ordinal() + 1) % values().length]; }
}
