package dev.xenos.economylogger.gui;

import dev.xenos.economylogger.EconomyLoggerPlugin;
import dev.xenos.economylogger.model.Page;
import dev.xenos.economylogger.model.Summary;
import dev.xenos.economylogger.util.Formats;
import dev.xenos.economylogger.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SourcesMenu extends Menu {

    private final LogMenu back;
    private final Map<Integer, String> pluginSlots = new HashMap<>();
    private final Map<Integer, Summary.Partner> partnerSlots = new HashMap<>();

    private SourcesMenu(EconomyLoggerPlugin plugin, Player viewer, LogMenu back) {
        super(plugin, viewer);
        this.back = back;
        this.inventory = Bukkit.createInventory(this, gui().rows("sources-menu.rows") * 9,
                gui().title("sources-menu.title", Map.of("player", Text.esc(back.targetName))));
    }

    public static void open(EconomyLoggerPlugin plugin, Player viewer, LogMenu back, Page page) {
        SourcesMenu m = new SourcesMenu(plugin, viewer, back);
        m.render(page.summary(), back.periodName(page.query().period()));
        viewer.openInventory(m.inventory);
    }

    private void render(Summary s, String period) {
        Gui g = gui();
        Formats f = plugin.formats();
        border("log-menu.border");
        Map<String, String> pp = Map.of("period", Text.esc(period));
        set(g.slot("sources-menu.plugins-header", 1), g.button("sources-menu.plugins-header", Material.COMPARATOR, pp));
        set(g.slot("sources-menu.partners-header", 7), g.button("sources-menu.partners-header", Material.PLAYER_HEAD, pp));

        List<Integer> ps = g.slots("sources-menu.plugin-slots");
        String unknown = g.label("unknown", "Unknown");
        for (int i = 0; i < s.plugins().size() && i < ps.size(); i++) {
            Summary.Source src = s.plugins().get(i);
            Map<String, String> ph = new HashMap<>();
            ph.put("plugin", Text.esc(src.plugin() == null ? unknown : src.plugin()));
            ph.put("income", f.money(src.income()));
            ph.put("spent", f.money(src.spent()));
            ph.put("net", f.money(src.income() - src.spent()));
            ph.put("in_count", Integer.toString(src.inCount()));
            ph.put("out_count", Integer.toString(src.outCount()));
            Material m = g.pluginIcon(src.plugin());
            if (m == null) m = g.material("sources-menu.plugin-material", Material.PAPER);
            set(ps.get(i), g.item(m, g.str("sources-menu.plugin-name", ""), g.list("sources-menu.plugin-lore"), ph));
            if (src.plugin() != null) pluginSlots.put(ps.get(i), src.plugin());
        }

        List<Integer> ts = g.slots("sources-menu.partner-slots");
        for (int i = 0; i < s.partners().size() && i < ts.size(); i++) {
            Summary.Partner p = s.partners().get(i);
            Map<String, String> ph = new HashMap<>();
            ph.put("player", Text.esc(p.name()));
            ph.put("income", f.money(p.income()));
            ph.put("spent", f.money(p.spent()));
            ph.put("net", f.money(p.income() - p.spent()));
            set(ts.get(i), back.head(p.uuid(), g.str("sources-menu.partner-name", ""), g.list("sources-menu.partner-lore"), ph));
            partnerSlots.put(ts.get(i), p);
        }
        set(g.slot("sources-menu.back", 49), g.button("sources-menu.back", Material.ARROW, Map.of()));
    }

    @Override
    public void click(int slot, ClickType type) {
        Gui g = gui();
        if (slot == g.slot("sources-menu.back", 49)) {
            g.sound(viewer, "click");
            back.reopen();
            return;
        }
        String pl = pluginSlots.get(slot);
        if (pl != null) {
            g.sound(viewer, "click");
            onViewer(() -> {
                viewer.openInventory(back.getInventory());
                back.apply(back.query().withPlugin(pl));
            });
            return;
        }
        Summary.Partner p = partnerSlots.get(slot);
        if (p != null && p.uuid() != null && viewer.hasPermission("economylogger.others")) {
            g.sound(viewer, "click");
            LogMenu.open(plugin, viewer, p.uuid(), p.name());
        }
    }
}
