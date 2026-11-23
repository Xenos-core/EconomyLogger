package dev.xenos.economylogger.command;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import dev.xenos.economylogger.gui.LogMenu;
import dev.xenos.economylogger.model.*;
import dev.xenos.economylogger.util.Formats;
import dev.xenos.economylogger.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class EcoLogCommand implements TabExecutor {

    private final EconomyLoggerPlugin plugin;

    public EcoLogCommand(EconomyLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { usage(sender); return true; }
            if (!perm(sender, "economylogger.use")) return true;
            LogMenu.open(plugin, p, p.getUniqueId(), p.getName());
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!perm(sender, "economylogger.admin")) return true;
                plugin.reload();
                msg(sender, "reloaded", Map.of());
            }
            case "stats" -> {
                if (!perm(sender, "economylogger.admin")) return true;
                stats(sender);
            }
            case "lookup" -> {
                if (!perm(sender, "economylogger.lookup")) return true;
                if (args.length < 2) { usage(sender); return true; }
                int page = 1;
                if (args.length > 2) try { page = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) { }
                lookup(sender, args[1], page);
            }
            case "help" -> usage(sender);
            default -> {
                if (!(sender instanceof Player p)) { usage(sender); return true; }
                if (!perm(sender, "economylogger.others")) return true;
                resolve(args[0]).thenAccept(t -> p.getScheduler().run(plugin, task -> {
                    if (t == null) msg(p, "player-not-found", Map.of("player", Text.esc(args[0])));
                    else LogMenu.open(plugin, p, t.getKey(), t.getValue());
                }, null));
            }
        }
        return true;
    }

    private CompletableFuture<Map.Entry<UUID, String>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return CompletableFuture.completedFuture(Map.entry(online.getUniqueId(), online.getName()));
        return plugin.database().findPlayer(name).thenApply(found -> {
            if (found != null) return found;
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            return cached == null ? null : Map.entry(cached.getUniqueId(), cached.getName() == null ? name : cached.getName());
        });
    }

    private void lookup(CommandSender sender, String name, int page) {
        resolve(name).thenCompose(t -> {
            if (t == null) return CompletableFuture.completedFuture(null);
            Query q = new Query(t.getKey(), Filter.ALL, Period.ALL, null, page - 1, 10);
            return plugin.database().page(q).thenApply(p -> Map.entry(t.getValue(), p));
        }).whenComplete((res, err) -> {
            if (err != null || res == null) {
                msg(sender, "player-not-found", Map.of("player", Text.esc(name)));
                return;
            }
            Page p = res.getValue();
            Formats f = plugin.formats();
            msg(sender, "lookup-header", Map.of("player", Text.esc(res.getKey()), "page", Integer.toString(page),
                    "total", Integer.toString(p.total())));
            if (p.entries().isEmpty()) { msg(sender, "lookup-empty", Map.of()); return; }
            var m = plugin.messages();
            String unknown = plugin.gui().label("unknown", "Unknown");
            for (LogEntry e : p.entries()) {
                boolean in = e.kind() == TxRecord.IN;
                String amount = (in ? "<in>+" : "<out>-") + f.money(e.amount()) + (in ? "</in>" : "</out>");
                String counter = e.counterName() == null ? "" : Text.fill(m.getString(in ? "lookup-counter-in" : "lookup-counter-out", ""),
                        Map.of("name", Text.esc(e.counterName())));
                String items = e.itemCount() == 0 ? "" : Text.fill(m.getString("lookup-items", ""), Map.of("count", Integer.toString(e.itemCount())));
                Map<String, String> ph = new HashMap<>();
                ph.put("date", Text.esc(f.date(e.time())));
                ph.put("amount", amount);
                ph.put("plugin", Text.esc(e.plugin() == null ? unknown : e.plugin()));
                ph.put("counter", counter);
                ph.put("items", items);
                raw(sender, m.getString("lookup-line", ""), ph);
            }
        });
    }

    private void stats(CommandSender sender) {
        plugin.database().stats().whenComplete((st, err) -> {
            Map<String, String> ph = new HashMap<>();
            ph.put("version", plugin.getPluginMeta().getVersion());
            ph.put("economy", Text.esc(plugin.hook().economyName()));
            ph.put("hook", Text.esc(plugin.hook().status()));
            ph.put("logged", Long.toString(plugin.recorder().logged()));
            ph.put("queued", Integer.toString(plugin.database().queued()));
            ph.put("dropped", Long.toString(plugin.database().dropped()));
            ph.put("rows", st == null ? "?" : Long.toString(st[0]));
            ph.put("size", st == null ? "?" : String.format(Locale.US, "%.1f MB", st[1] / 1048576.0));
            for (String line : plugin.messages().getStringList("stats")) raw(sender, line, ph);
        });
    }

    private void usage(CommandSender s) {
        for (String line : plugin.messages().getStringList("usage")) raw(s, line, Map.of());
    }

    private boolean perm(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return true;
        msg(s, "no-permission", Map.of());
        return false;
    }

    private void msg(CommandSender s, String key, Map<String, String> ph) {
        String prefix = plugin.messages().getString("prefix", "");
        raw(s, prefix + plugin.messages().getString(key, key), ph);
    }

    private void raw(CommandSender s, String text, Map<String, String> ph) {
        s.sendMessage(plugin.gui().text.parse(text, ph));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("economylogger.admin")) out.addAll(List.of("reload", "stats"));
            if (sender.hasPermission("economylogger.lookup")) out.add("lookup");
            if (sender.hasPermission("economylogger.others")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lookup") && sender.hasPermission("economylogger.lookup")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String pre = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(pre));
        return out;
    }
}
