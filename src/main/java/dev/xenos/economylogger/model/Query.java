package dev.xenos.economylogger.model;

import java.util.UUID;

public record Query(UUID player, Filter filter, Period period, String plugin, int page, int pageSize) {
    public Query withPage(int p) { return new Query(player, filter, period, plugin, p, pageSize); }
    public Query withFilter(Filter f) { return new Query(player, f, period, plugin, 0, pageSize); }
    public Query withPeriod(Period p) { return new Query(player, filter, p, plugin, 0, pageSize); }
    public Query withPlugin(String pl) { return new Query(player, filter, period, pl, 0, pageSize); }
}
