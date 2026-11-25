package dev.xenos.economylogger.track;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import dev.xenos.economylogger.model.TxItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemTracker {

    private static final class Session {
        final long txId;
        final ItemStack[] before;
        Session(long txId, ItemStack[] before) { this.txId = txId; this.before = before; }
    }

    private final EconomyLoggerPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ItemTracker(EconomyLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    public void track(long txId, Player player, String source) {
        var s = plugin.settings();
        if (!s.items() || s.itemsIgnored(source) || !player.isOnline()) return;
        if (!Bukkit.isOwnedByCurrentRegion(player)) return;
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) return;
        Session session = new Session(txId, copy(player.getInventory().getContents()));
        if (sessions.putIfAbsent(id, session) != null) return;
        var task = player.getScheduler().runDelayed(plugin, t -> finish(player, session),
                () -> sessions.remove(id, session), s.itemDelayTicks());
        if (task == null) sessions.remove(id, session);
    }

    private void finish(Player player, Session session) {
        sessions.remove(player.getUniqueId(), session);
        if (!plugin.isEnabled()) return;
        List<TxItem> diff = diff(session.before, player.getInventory().getContents(), plugin.settings().itemMax());
        plugin.database().items(session.txId, diff);
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] out = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && !it.getType().isAir()) out[i] = it.clone();
        }
        return out;
    }

    private record Group(ItemStack proto, int[] count) {}

    private static List<Group> group(ItemStack[] items, List<Group> groups, int sign) {
        outer:
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            for (Group g : groups) {
                if (g.proto.isSimilar(it)) { g.count[0] += sign * it.getAmount(); continue outer; }
            }
            groups.add(new Group(it.asOne(), new int[]{sign * it.getAmount()}));
        }
        return groups;
    }

    static List<TxItem> diff(ItemStack[] before, ItemStack[] after, int max) {
        List<Group> groups = new ArrayList<>();
        group(after, groups, 1);
        group(before, groups, -1);
        List<TxItem> out = new ArrayList<>();
        for (Group g : groups) {
            int d = g.count[0];
            if (d == 0) continue;
            byte[] data;
            try { data = g.proto.serializeAsBytes(); } catch (Throwable t) { data = null; }
            out.add(new TxItem(d, data, label(g.proto)));
            if (out.size() >= max) break;
        }
        out.sort((a, b) -> Integer.compare(b.delta(), a.delta()));
        return out;
    }

    static String label(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            String plain = dev.xenos.economylogger.util.Text.plain(name).strip();
            if (!plain.isEmpty()) return plain;
        }
        return pretty(it.getType().name());
    }

    private static String pretty(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String part : enumName.toLowerCase().split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
