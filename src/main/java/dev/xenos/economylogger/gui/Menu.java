package dev.xenos.economylogger.gui;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class Menu implements InventoryHolder {

    protected final EconomyLoggerPlugin plugin;
    protected final Player viewer;
    protected Inventory inventory;
    protected volatile boolean busy;

    protected Menu(EconomyLoggerPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    protected Gui gui() { return plugin.gui(); }

    public abstract void click(int slot, ClickType type);

    protected void border(String path) {
        if (!gui().bool(path + ".enabled", true)) return;
        List<String> mats = gui().list(path + ".materials");
        ItemStack[] panes = mats.isEmpty() ? new ItemStack[]{gui().pane(Material.LIME_STAINED_GLASS_PANE)}
                : mats.stream().map(m -> {
                    Material mat = Material.matchMaterial(m);
                    return gui().pane(mat == null ? Material.LIME_STAINED_GLASS_PANE : mat);
                }).toArray(ItemStack[]::new);
        int size = inventory.getSize();
        int rows = size / 9;
        int i = 0;
        for (int s = 0; s < size; s++) {
            int r = s / 9, c = s % 9;
            if (r == 0 || r == rows - 1 || c == 0 || c == 8) inventory.setItem(s, panes[(r + c) % panes.length]);
            i++;
        }
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    protected boolean viewing() {
        return viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder(false) == this;
    }

    protected void onViewer(Runnable r) {
        viewer.getScheduler().run(plugin, t -> r.run(), null);
    }
}
