package dev.xenos.economylogger;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record Settings(
        boolean logFailed, double minAmount, Set<String> ignoredPlugins, Set<String> ignoredPlayers,
        boolean pairing, long pairingWindowNanos, double pairingMinRatio,
        boolean ctxCommands, boolean ctxMenus, boolean ctxConsole, long ctxWindowNanos, int ctxMaxLength,
        boolean items, int itemDelayTicks, Set<String> itemIgnoredPlugins, int itemMax,
        boolean essentials, Set<String> essentialsCauses) {

    public static Settings from(FileConfiguration c) {
        return new Settings(
                c.getBoolean("hook.log-failed", false),
                c.getDouble("tracking.min-amount", 0),
                lower(c.getStringList("tracking.ignored-plugins")),
                lower(c.getStringList("tracking.ignored-players")),
                c.getBoolean("tracking.pairing.enabled", true),
                Math.max(1, c.getLong("tracking.pairing.window-ms", 100)) * 1_000_000L,
                c.getDouble("tracking.pairing.min-ratio", 0.5),
                c.getBoolean("tracking.context.commands", true),
                c.getBoolean("tracking.context.menus", true),
                c.getBoolean("tracking.context.console", true),
                Math.max(1, c.getLong("tracking.context.window-ms", 100)) * 1_000_000L,
                Math.max(8, c.getInt("tracking.context.max-length", 64)),
                c.getBoolean("items.enabled", true),
                Math.max(1, c.getInt("items.delay-ticks", 2)),
                lower(c.getStringList("items.ignored-plugins")),
                Math.max(1, c.getInt("items.max-per-transaction", 27)),
                c.getBoolean("essentials.enabled", true),
                upper(c.getStringList("essentials.causes")));
    }

    private static Set<String> lower(List<String> l) {
        Set<String> s = new HashSet<>();
        for (String x : l) s.add(x.toLowerCase(Locale.ROOT));
        return Set.copyOf(s);
    }

    private static Set<String> upper(List<String> l) {
        Set<String> s = new HashSet<>();
        for (String x : l) s.add(x.toUpperCase(Locale.ROOT));
        return Set.copyOf(s);
    }

    public boolean pluginIgnored(String plugin) {
        return plugin != null && ignoredPlugins.contains(plugin.toLowerCase(Locale.ROOT));
    }

    public boolean playerIgnored(String name) {
        return name != null && ignoredPlayers.contains(name.toLowerCase(Locale.ROOT));
    }

    public boolean itemsIgnored(String plugin) {
        return plugin != null && itemIgnoredPlugins.contains(plugin.toLowerCase(Locale.ROOT));
    }
}
