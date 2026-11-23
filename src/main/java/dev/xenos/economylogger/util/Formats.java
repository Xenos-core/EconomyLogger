package dev.xenos.economylogger.util;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

public final class Formats {
    private final String prefix;
    private final String suffix;
    private final ThreadLocal<DecimalFormat> number;
    private final boolean economyFormat;
    private final DateTimeFormatter date;
    private final Supplier<Economy> economy;
    private final ConfigurationSection ago;

    public Formats(FileConfiguration cfg, ConfigurationSection ago, Supplier<Economy> economy) {
        this.prefix = cfg.getString("format.currency-prefix", "$");
        this.suffix = cfg.getString("format.currency-suffix", "");
        int decimals = Math.max(0, Math.min(8, cfg.getInt("format.decimals", 2)));
        String pattern = "#,##0" + (decimals > 0 ? "." + "0".repeat(decimals) : "");
        this.number = ThreadLocal.withInitial(() -> new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US)));
        this.economyFormat = cfg.getBoolean("format.use-economy-format", false);
        String tz = cfg.getString("format.timezone", "");
        ZoneId zone;
        try { zone = tz == null || tz.isBlank() ? ZoneId.systemDefault() : ZoneId.of(tz); } catch (Exception e) { zone = ZoneId.systemDefault(); }
        DateTimeFormatter f;
        try { f = DateTimeFormatter.ofPattern(cfg.getString("format.date", "yyyy-MM-dd HH:mm")); } catch (Exception e) { f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"); }
        this.date = f.withZone(zone);
        this.economy = economy;
        this.ago = ago;
    }

    public String money(double v) {
        if (Double.isNaN(v)) return "";
        if (economyFormat) {
            Economy e = economy.get();
            if (e != null) {
                try { return e.format(v); } catch (Throwable ignored) { }
            }
        }
        return (v < 0 ? "-" : "") + prefix + number.get().format(Math.abs(v)) + suffix;
    }

    public String date(long millis) { return date.format(Instant.ofEpochMilli(millis)); }

    public String ago(long millis) {
        long s = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
        String key; long n;
        if (s < 5) { key = "now"; n = 0; }
        else if (s < 60) { key = "seconds"; n = s; }
        else if (s < 3600) { key = "minutes"; n = s / 60; }
        else if (s < 86400) { key = "hours"; n = s / 3600; }
        else { key = "days"; n = s / 86400; }
        String def = switch (key) { case "now" -> "just now"; case "seconds" -> "{n}s ago"; case "minutes" -> "{n}m ago"; case "hours" -> "{n}h ago"; default -> "{n}d ago"; };
        String t = ago == null ? def : ago.getString(key, def);
        return t.replace("{n}", Long.toString(n));
    }
}
