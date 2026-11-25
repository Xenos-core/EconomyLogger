package dev.xenos.economylogger.model;

import java.util.UUID;

public final class TxRecord {
    public static final byte IN = 0;
    public static final byte OUT = 1;

    public final long id;
    public final long time;
    public final UUID player;
    public final String playerName;
    public final byte kind;
    public final double amount;
    public final double balance;
    public final boolean success;
    public final String plugin;
    public final String context;
    public final String world;

    public UUID counterUuid;
    public String counterName;
    public long linkId;

    public TxRecord(long id, long time, UUID player, String playerName, byte kind, double amount, double balance,
                    boolean success, String plugin, String context, String world) {
        this.id = id;
        this.time = time;
        this.player = player;
        this.playerName = playerName;
        this.kind = kind;
        this.amount = amount;
        this.balance = balance;
        this.success = success;
        this.plugin = plugin;
        this.context = context;
        this.world = world;
    }

    public boolean samePlayer(TxRecord o) {
        if (player != null && o.player != null) return player.equals(o.player);
        return playerName != null && playerName.equalsIgnoreCase(o.playerName);
    }
}
