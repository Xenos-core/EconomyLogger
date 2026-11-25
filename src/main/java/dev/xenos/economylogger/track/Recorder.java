package dev.xenos.economylogger.track;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import dev.xenos.economylogger.Settings;
import dev.xenos.economylogger.model.TxRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class Recorder {

    private static final class Last {
        TxRecord in, out;
        long inNanos, outNanos;
    }

    private final EconomyLoggerPlugin plugin;
    private final CallerResolver callers;
    private final ContextTracker context;
    private final ItemTracker items;
    private final ThreadLocal<Last> last = ThreadLocal.withInitial(Last::new);
    private final AtomicLong logged = new AtomicLong();

    public Recorder(EconomyLoggerPlugin plugin, CallerResolver callers, ContextTracker context, ItemTracker items) {
        this.plugin = plugin;
        this.callers = callers;
        this.context = context;
        this.items = items;
    }

    public long logged() { return logged.get(); }

    public String caller() { return callers.caller(); }

    public String caller(String skip) { return callers.caller(skip); }

    public void record(OfflinePlayer player, String name, boolean deposit, double amount, double balance,
                       boolean success, String source, String world, String fixedContext) {
        if (!plugin.isEnabled()) return;
        Settings s = plugin.settings();
        if (!success && !s.logFailed()) return;
        if (!(amount > 0) || amount < s.minAmount()) return;
        if (s.pluginIgnored(source)) return;
        UUID uuid = null;
        if (player != null) {
            uuid = player.getUniqueId();
            if (name == null) name = player.getName();
        } else if (name != null) {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null) uuid = cached.getUniqueId();
        }
        if (uuid == null && name == null) return;
        if (s.playerIgnored(name)) return;

        Player online = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (world == null && online != null) world = online.getWorld().getName();

        long now = System.nanoTime();
        byte kind = deposit ? TxRecord.IN : TxRecord.OUT;
        String ctx = fixedContext;
        TxRecord r = new TxRecord(plugin.database().nextId(), System.currentTimeMillis(), uuid, name, kind,
                amount, balance, success, source, null, world);

        TxRecord other = null;
        if (success && s.pairing()) {
            Last l = last.get();
            TxRecord cand = deposit ? l.out : l.in;
            long t = deposit ? l.outNanos : l.inNanos;
            if (cand != null && now - t <= s.pairingWindowNanos() && !cand.samePlayer(r)
                    && java.util.Objects.equals(cand.plugin, source)
                    && Math.min(cand.amount, amount) / Math.max(cand.amount, amount) >= s.pairingMinRatio()) {
                other = cand;
                r.counterUuid = cand.player;
                r.counterName = cand.playerName;
                r.linkId = cand.id;
            }
            if (deposit) { l.in = r; l.inNanos = now; } else { l.out = r; l.outNanos = now; }
        }

        String auto = context.context(uuid, other == null ? null : other.player);
        String finalCtx = auto == null ? ctx : (ctx == null || auto.startsWith(ctx) ? auto : ctx + " · " + auto);
        TxRecord rec = finalCtx == null ? r : withContext(r, finalCtx);

        plugin.database().insert(rec);
        if (other != null) plugin.database().link(other.id, rec.player, rec.playerName, rec.id);
        logged.incrementAndGet();

        if (success && online != null) items.track(rec.id, online, source);
    }

    private static TxRecord withContext(TxRecord r, String ctx) {
        TxRecord n = new TxRecord(r.id, r.time, r.player, r.playerName, r.kind, r.amount, r.balance, r.success, r.plugin, ctx, r.world);
        n.counterUuid = r.counterUuid;
        n.counterName = r.counterName;
        n.linkId = r.linkId;
        return n;
    }
}
