package dev.xenos.economylogger.gui;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import dev.xenos.economylogger.model.LogEntry;
import dev.xenos.economylogger.model.TxItem;
import dev.xenos.economylogger.model.TxRecord;
import dev.xenos.economylogger.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemsMenu extends Menu {

    private final LogMenu back;

    private ItemsMenu(EconomyLoggerPlugin plugin, Player viewer, LogMenu back, LogEntry e) {
        super(plugin, viewer);
        this.back = back;
        String amt = (e.kind() == TxRecord.IN ? "+" : "-") + plugin.formats().money(e.amount());
        this.inventory = Bukkit.createInventory(this, gui().rows("items-menu.rows") * 9,
                gui().title("items-menu.title", Map.of("amount", Text.esc(amt))));
    }

    public static void open(EconomyLoggerPlugin plugin, Player viewer, LogMenu back, LogEntry e) {
        back.busy = true;
        plugin.database().items(e.id()).whenComplete((items, err) -> back.onViewer(() -> {
            back.busy = false;
            if (err != null || items == null) return;
            ItemsMenu m = new ItemsMenu(plugin, viewer, back, e);
            m.render(items);
            viewer.openInventory(m.inventory);
        }));
    }

    private void render(List<TxItem> items) {
        Gui g = gui();
        border("log-menu.border");
        int size = inventory.getSize();
        List<Integer> free = new ArrayList<>();
        for (int s = 0; s < size; s++) {
            int r = s / 9, c = s % 9;
            if (r > 0 && r < size / 9 - 1 && c > 0 && c < 8) free.add(s);
        }
        int i = 0;
        for (TxItem t : items) {
            if (i >= free.size()) break;
            ItemStack it;
            try {
                it = t.data() == null ? new ItemStack(Material.PAPER) : ItemStack.deserializeBytes(t.data());
            } catch (Throwable ex) {
                it = new ItemStack(Material.PAPER);
            }
            int n = Math.abs(t.delta());
            it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), n)));
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.addAll(g.text.lore(g.list(t.delta() > 0 ? "items-menu.received-lore" : "items-menu.removed-lore"),
                        Map.of("amount", Integer.toString(n))));
                meta.lore(lore);
                if (!meta.hasDisplayName() && t.data() == null) meta.displayName(g.text.item("<text>" + Text.esc(t.label()), Map.of()));
                it.setItemMeta(meta);
            }
            inventory.setItem(free.get(i++), it);
        }
        set(g.slot("items-menu.back", 49), g.button("items-menu.back", Material.ARROW, Map.of()));
    }

    @Override
    public void click(int slot, ClickType type) {
        if (slot == gui().slot("items-menu.back", 49)) {
            gui().sound(viewer, "click");
            back.reopen();
        }
    }
}
