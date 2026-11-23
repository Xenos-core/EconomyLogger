package dev.xenos.economylogger.model;

import java.util.List;
import java.util.UUID;

/** A transaction read back from the database for display. */
public record LogEntry(long id, long time, UUID player, String playerName, byte kind, double amount, double balance,
                       String plugin, UUID counterUuid, String counterName, String context, String world,
                       int itemCount, List<TxItem> preview) {
}
