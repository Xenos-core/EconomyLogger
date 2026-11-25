package dev.xenos.economylogger.gui;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import dev.xenos.economylogger.model.*;
import dev.xenos.economylogger.util.Formats;
import dev.xenos.economylogger.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class LogMenu extends Menu {

    final UUID target;
    final String targetName;
    private Query query;
    private Page page;
    private double balance = Double.NaN;
    private final Map<Integer, LogEntry> bySlot = new HashMap<>();

    private LogMenu(EconomyLoggerPlugin plugin, Player viewer, UUID target, String targetName, Query query) {
        super(plugin, viewer);
        this.target = target;
        this.targetName = targetName;
        this.query = query;
        Gui g = gui();
        int rows = g.rows("log-menu.rows");
        this.inventory = Bukkit.createInventory(this, rows * 9, g.title("log-menu.title", Map.of("player", Text.esc(targetName))));
    }

    public static void open(EconomyLoggerPlugin plugin, Player viewer, UUID target, String name) {
        int size = plugin.gui().slots("log-menu.entry-slots").size();
        LogMenu m = new LogMenu(plugin, viewer, target, name,
                new Query(target, Filter.ALL, Period.WEEK, null, 0, Math.max(1, size)));
        m.load(true);
    }

    Query query() { return query; }

    void reopen() {
        onViewer(() -> {
            viewer.openInventory(inventory);
            load(false);
        });
    }

    void apply(Query q) {
        query = q;
        load(false);
    }

    private void load(boolean openAfter) {
        busy = true;
        OfflinePlayer off = Bukkit.getOfflinePlayer(target);
        plugin.database().page(query).thenApply(p -> {
            double bal = Double.NaN;
            try {
                var eco = plugin.hook().delegate();
                if (eco != null) bal = eco.getBalance(off);
            } catch (Throwable ignored) { }
            balance = bal;
            return p;
        }).whenComplete((p, err) -> onViewer(() -> {
            busy = false;
            if (err != null) {
                plugin.getLogger().warning("Could not load log: " + err.getMessage());
                return;
            }
            page = p;
            render();
            if (openAfter) {
                viewer.openInventory(inventory);
                gui().sound(viewer, "open");
            }
        }));
    }

    private void render() {
        Gui g = gui();
        Formats f = plugin.formats();
        inventory.clear();
        bySlot.clear();
        border("log-menu.border");

        Summary s = page.summary();
        Map<String, String> ph = new HashMap<>();
        ph.put("player", Text.esc(targetName));
        ph.put("period", Text.esc(periodName(query.period())));
        ph.put("income", f.money(s.income()));
        ph.put("spent", f.money(s.spent()));
        ph.put("net", f.money(s.income() - s.spent()));
        ph.put("count", Integer.toString(s.count()));
        ph.put("balance", Double.isNaN(balance) ? "" : f.money(balance));
        ItemStack head = head(target, g.str("log-menu.summary.name", ""), g.list("log-menu.summary.lore"), ph);
        set(g.slot("log-menu.summary", 4), head);

        List<Integer> slots = g.slots("log-menu.entry-slots");
        List<LogEntry> entries = page.entries();
        for (int i = 0; i < entries.size() && i < slots.size(); i++) {
            LogEntry e = entries.get(i);
            set(slots.get(i), entry(e));
            bySlot.put(slots.get(i), e);
        }
        if (entries.isEmpty()) {
            set(g.slot("log-menu.empty", 22), g.button("log-menu.empty", Material.LIGHT_GRAY_STAINED_GLASS_PANE, Map.of()));
        }

        Map<String, String> pg = Map.of("page", Integer.toString(query.page() + 1), "pages", Integer.toString(page.pages()));
        if (query.page() > 0) set(g.slot("log-menu.buttons.previous", 45), g.button("log-menu.buttons.previous", Material.ARROW, pg));
        if (query.page() + 1 < page.pages()) set(g.slot("log-menu.buttons.next", 53), g.button("log-menu.buttons.next", Material.ARROW, pg));

        List<String> types = Arrays.stream(Filter.values()).map(this::filterName).toList();
        set(g.slot("log-menu.buttons.type", 47), optionButton("log-menu.buttons.type", Material.HOPPER, types, query.filter().ordinal()));
        List<String> periods = Arrays.stream(Period.values()).map(this::periodName).toList();
        set(g.slot("log-menu.buttons.period", 50), optionButton("log-menu.buttons.period", Material.CLOCK, periods, query.period().ordinal()));

        String pv = query.plugin() == null ? g.label("all-plugins", "All plugins") : query.plugin();
        set(g.slot("log-menu.buttons.plugin", 48), g.button("log-menu.buttons.plugin", Material.COMPARATOR, Map.of("value", Text.esc(pv))));
        set(g.slot("log-menu.buttons.close", 49), g.button("log-menu.buttons.close", Material.BARRIER, Map.of()));
        set(g.slot("log-menu.buttons.sources", 51), g.button("log-menu.buttons.sources", Material.KNOWLEDGE_BOOK, Map.of()));
    }

    private ItemStack optionButton(String path, Material def, List<String> options, int selected) {
        Gui g = gui();
        List<String> lore = g.expand(g.list(path + ".lore"), "options", g.options(options, selected));
        return g.item(g.material(path + ".material", def), g.str(path + ".name", ""), lore, Map.of());
    }

    String filterName(Filter f) { return gui().label("types." + f.name(), f.name()); }

    String periodName(Period p) { return gui().label("periods." + p.name(), p.name()); }

    ItemStack head(UUID owner, String name, List<String> lore, Map<String, String> ph) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        if (owner != null && it.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            it.setItemMeta(sm);
        }
        Gui.decorate(it, gui().text.item(name, ph), gui().text.lore(lore, ph));
        return it;
    }

    private ItemStack entry(LogEntry e) {
        Gui g = gui();
        Formats f = plugin.formats();
        boolean in = e.kind() == TxRecord.IN;
        String unknown = g.label("unknown", "Unknown");

        Map<String, String> ph = new HashMap<>();
        ph.put("amount", f.money(e.amount()));
        ph.put("plugin", Text.esc(e.plugin() == null ? unknown : e.plugin()));
        ph.put("from", in && e.counterName() != null ? Text.esc(e.counterName()) : "");
        ph.put("to", !in && e.counterName() != null ? Text.esc(e.counterName()) : "");
        ph.put("context", e.context() == null ? "" : Text.esc(e.context()));
        ph.put("world", e.world() == null ? "" : Text.esc(e.world()));
        ph.put("ago", Text.esc(f.ago(e.time())));
        ph.put("date", Text.esc(f.date(e.time())));
        ph.put("balance", Double.isNaN(e.balance()) ? "" : f.money(e.balance()));

        List<String> lore = new ArrayList<>(g.list("entry.lore"));
        if (e.itemCount() > 0) {
            lore.addAll(g.list("entry.items-header"));
            int max = g.num("entry.items-preview", 4);
            int shown = 0;
            for (TxItem it : e.preview()) {
                if (shown >= max) break;
                String tpl = it.delta() > 0 ? g.str("entry.item-received", "") : g.str("entry.item-removed", "");
                lore.add(Text.fill(tpl, Map.of("amount", Integer.toString(Math.abs(it.delta())), "item", Text.esc(it.label()))));
                shown++;
            }
            if (e.itemCount() > shown) lore.add(Text.fill(g.str("entry.items-more", ""), Map.of("more", Integer.toString(e.itemCount() - shown))));
            lore.addAll(g.list("entry.items-footer"));
        }

        ItemStack icon = icon(e, in);
        Gui.decorate(icon, g.text.item(g.str(in ? "entry.name-income" : "entry.name-spend", "{amount}"), ph), g.text.lore(lore, ph));
        return icon;
    }

    private ItemStack icon(LogEntry e, boolean in) {
        Gui g = gui();
        if (g.bool("entry.show-received-item", true)) {
            for (TxItem it : e.preview()) {
                if (it.delta() > 0 && it.data() != null) {
                    try {
                        ItemStack s = ItemStack.deserializeBytes(it.data());
                        s.setAmount(Math.max(1, Math.min(s.getMaxStackSize(), it.delta())));
                        return s;
                    } catch (Throwable ignored) { }
                }
            }
        }
        if (e.counterName() != null && g.bool("entry.transfer-uses-head", true)) {
            ItemStack it = new ItemStack(Material.PLAYER_HEAD);
            if (e.counterUuid() != null && it.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(e.counterUuid()));
                it.setItemMeta(sm);
            }
            return it;
        }
        Material m = g.pluginIcon(e.plugin());
        if (m == null) m = g.material(in ? "entry.income-material" : "entry.spend-material", in ? Material.EMERALD : Material.REDSTONE);
        return new ItemStack(m);
    }

    @Override
    public void click(int slot, ClickType type) {
        Gui g = gui();
        LogEntry e = bySlot.get(slot);
        if (e != null) {
            if (e.itemCount() > 0) {
                g.sound(viewer, "click");
                ItemsMenu.open(plugin, viewer, this, e);
            } else if (type.isRightClick() && e.counterUuid() != null && viewer.hasPermission("economylogger.others")) {
                g.sound(viewer, "click");
                LogMenu.open(plugin, viewer, e.counterUuid(), e.counterName());
            }
            return;
        }
        if (page == null) return;
        if (slot == g.slot("log-menu.buttons.previous", 45) && query.page() > 0) {
            g.sound(viewer, "page");
            apply(query.withPage(query.page() - 1));
        } else if (slot == g.slot("log-menu.buttons.next", 53) && query.page() + 1 < page.pages()) {
            g.sound(viewer, "page");
            apply(query.withPage(query.page() + 1));
        } else if (slot == g.slot("log-menu.buttons.type", 47)) {
            g.sound(viewer, "click");
            apply(query.withFilter(query.filter().next()));
        } else if (slot == g.slot("log-menu.buttons.period", 50)) {
            g.sound(viewer, "click");
            apply(query.withPeriod(query.period().next()));
        } else if (slot == g.slot("log-menu.buttons.plugin", 48)) {
            g.sound(viewer, "click");
            List<String> list = page.plugins();
            String next = null;
            if (!type.isRightClick() && !list.isEmpty()) {
                int i = query.plugin() == null ? -1 : list.indexOf(query.plugin());
                next = i + 1 < list.size() ? list.get(i + 1) : null;
            }
            apply(query.withPlugin(next));
        } else if (slot == g.slot("log-menu.buttons.sources", 51)) {
            g.sound(viewer, "click");
            Page current = page;
            onViewer(() -> SourcesMenu.open(plugin, viewer, this, current));
        } else if (slot == g.slot("log-menu.buttons.close", 49)) {
            onViewer(viewer::closeInventory);
        }
    }
}
