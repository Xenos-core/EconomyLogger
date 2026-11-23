package dev.xenos.economylogger.model;

public enum Filter {
    ALL, INCOME, SPEND, PAYMENTS, ITEMS;

    public Filter next() { return values()[(ordinal() + 1) % values().length]; }
}
