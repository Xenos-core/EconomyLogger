package dev.xenos.economylogger.hook;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import dev.xenos.economylogger.track.Recorder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.logging.Level;

public final class EssentialsHook implements Listener {

    private static final String EVENT = "net.ess3.api.events.UserBalanceUpdateEvent";

    private final EconomyLoggerPlugin plugin;
    private final Recorder recorder;
    private MethodHandle getPlayer, getOld, getNew, getCause;
    private boolean hooked;

    public EssentialsHook(EconomyLoggerPlugin plugin, Recorder recorder) {
        this.plugin = plugin;
        this.recorder = recorder;
    }

    public boolean hooked() { return hooked; }

    @SuppressWarnings("unchecked")
    public void tryHook() {
        if (hooked || !plugin.settings().essentials()) return;
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) return;
        try {
            Class<?> cls = Class.forName(EVENT, true, ess.getClass().getClassLoader());
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            getPlayer = l.findVirtual(cls, "getPlayer", MethodType.methodType(Player.class));
            getOld = l.findVirtual(cls, "getOldBalance", MethodType.methodType(BigDecimal.class));
            getNew = l.findVirtual(cls, "getNewBalance", MethodType.methodType(BigDecimal.class));
            Class<?> cause = Class.forName(EVENT + "$Cause", true, ess.getClass().getClassLoader());
            getCause = l.findVirtual(cls, "getCause", MethodType.methodType(cause));
            Bukkit.getPluginManager().registerEvent((Class<? extends Event>) cls, this, EventPriority.MONITOR,
                    (listener, event) -> { if (cls.isInstance(event)) handle(event); }, plugin, false);
            hooked = true;
            plugin.getLogger().info("Hooked into Essentials balance changes (/pay, /eco, /sell).");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Could not hook Essentials balance events", t);
        }
    }

    public void unhook() {
        HandlerList.unregisterAll(this);
        hooked = false;
    }

    private void handle(Event e) {
        if (TrackedEconomy.inTrackedCall()) return;
        try {
            String cause = String.valueOf(getCause.invoke(e)).toUpperCase(Locale.ROOT);
            if (!plugin.settings().essentialsCauses().contains(cause)) return;
            Player p = (Player) getPlayer.invoke(e);
            BigDecimal oldB = (BigDecimal) getOld.invoke(e);
            BigDecimal newB = (BigDecimal) getNew.invoke(e);
            if (p == null || oldB == null || newB == null) return;
            double diff = newB.subtract(oldB).doubleValue();
            if (diff == 0) return;
            String source = "Essentials";
            String ctx = switch (cause) {
                case "COMMAND_PAY" -> "/pay";
                case "COMMAND_ECO" -> "/eco";
                case "COMMAND_SELL" -> "/sell";
                default -> null;
            };
            if (cause.equals("API")) {
                String caller = recorder.caller("Essentials");
                if (caller != null) source = caller;
            }
            recorder.record(p, p.getName(), diff > 0, Math.abs(diff), newB.doubleValue(), true, source, null, ctx);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Essentials event", t);
        }
    }
}
