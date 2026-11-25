package dev.xenos.economylogger.track;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import dev.xenos.economylogger.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.UUID;

public final class ContextTracker implements Listener {

    private static final class Ctx {
        UUID player;
        String command;
        Component menu;
        long nanos;
    }

    private final EconomyLoggerPlugin plugin;
    private final ThreadLocal<Ctx> current = ThreadLocal.withInitial(Ctx::new);

    public ContextTracker(EconomyLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.settings().ctxCommands()) return;
        Ctx c = current.get();
        c.player = e.getPlayer().getUniqueId();
        c.command = e.getMessage();
        c.menu = null;
        c.nanos = System.nanoTime();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsole(ServerCommandEvent e) {
        if (!plugin.settings().ctxConsole()) return;
        Ctx c = current.get();
        c.player = null;
        c.command = "console: /" + e.getCommand();
        c.menu = null;
        c.nanos = System.nanoTime();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!plugin.settings().ctxMenus()) return;
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;
        Ctx c = current.get();
        c.player = e.getWhoClicked().getUniqueId();
        c.command = null;
        c.menu = e.getView().title();
        c.nanos = System.nanoTime();
    }

    public String context(UUID a, UUID b) {
        Ctx c = current.get();
        if (c.nanos == 0 || System.nanoTime() - c.nanos > plugin.settings().ctxWindowNanos()) return null;
        boolean console = c.player == null && c.command != null;
        if (!console && c.player != null && !c.player.equals(a) && !c.player.equals(b)) return null;
        String s;
        if (c.command != null) s = c.command;
        else if (c.menu != null) s = "menu: " + Text.plain(c.menu).strip();
        else return null;
        int max = plugin.settings().ctxMaxLength();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
