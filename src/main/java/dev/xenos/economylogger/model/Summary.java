package dev.xenos.economylogger.model;

import java.util.List;
import java.util.UUID;

public record Summary(double income, double spent, int count, List<Source> plugins, List<Partner> partners) {
    public record Source(String plugin, double income, double spent, int inCount, int outCount) {}
    public record Partner(UUID uuid, String name, double income, double spent) {}
}
