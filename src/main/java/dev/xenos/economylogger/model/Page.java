package dev.xenos.economylogger.model;

import java.util.List;

public record Page(Query query, List<LogEntry> entries, int total, Summary summary, List<String> plugins) {
    public int pages() { return Math.max(1, (total + query.pageSize() - 1) / query.pageSize()); }
}
