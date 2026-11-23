package dev.xenos.economylogger.hook;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import dev.xenos.economylogger.track.Recorder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.util.logging.Level;

/**
 * Registers {@link TrackedEconomy} at the highest priority as soon as the server starts, so every plugin
 * that asks Vault for an economy gets the recorder. The real economy is found lazily and follows
 * any later (un)registration.
 */
public final class EconomyHook implements Listener {

    private final EconomyLoggerPlugin plugin;
    private final TrackedEconomy tracked;
    private volatile Economy delegate;
    private volatile boolean resolved;
    private volatile boolean active;
    private long lastError;

    public EconomyHook(EconomyLoggerPlugin plugin, Recorder recorder) {
        this.plugin = plugin;
        this.tracked = new TrackedEconomy(this, recorder);
    }

    public void register() {
        Bukkit.getServicesManager().register(Economy.class, tracked, plugin, ServicePriority.Highest);
        active = true;
        resolved = false;
    }

    public void unregister() {
        active = false;
        Bukkit.getServicesManager().unregister(Economy.class, tracked);
    }

    public boolean active() { return active; }

    /** The real economy plugin's provider (never our own wrapper). */
    public Economy delegate() {
        Economy d = delegate;
        if (d == null && !resolved) d = resolve();
        return d;
    }

    private synchronized Economy resolve() {
        Economy found = null;
        for (RegisteredServiceProvider<Economy> r : Bukkit.getServicesManager().getRegistrations(Economy.class)) {
            Economy p = r.getProvider();
            if (p instanceof TrackedEconomy || p.getClass().getName().equals(TrackedEconomy.class.getName())) continue;
            found = p;
            break;
        }
        delegate = found;
        resolved = found != null;
        return found;
    }

    /** Whether other plugins currently receive our recorder when they ask Vault for an economy. */
    public String status() {
        if (!active) return "disabled";
        RegisteredServiceProvider<Economy> top = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (top == null) return "no economy";
        if (top.getProvider() == tracked) return delegate() == null ? "waiting for an economy plugin" : "active";
        return "bypassed by " + top.getPlugin().getName();
    }

    public String economyName() {
        Economy d = delegate();
        return d == null ? "none" : d.getName();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegister(ServiceRegisterEvent e) {
        if (e.getProvider().getService() != Economy.class) return;
        resolved = false;
        delegate = null;
        if (active && e.getProvider().getProvider() != tracked) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                String s = status();
                if (s.startsWith("bypassed")) plugin.getLogger().warning("Another economy was registered at Highest priority before EconomyLogger (" + s
                        + "). Plugins that ask Vault will skip the logger.");
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnregister(ServiceUnregisterEvent e) {
        if (e.getProvider().getService() != Economy.class) return;
        resolved = false;
        delegate = null;
    }

    void error(Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastError > 60_000) {
            lastError = now;
            plugin.getLogger().log(Level.WARNING, "Could not record a transaction (economy calls are unaffected)", t);
        }
    }
}
