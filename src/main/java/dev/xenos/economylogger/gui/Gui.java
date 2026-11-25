package dev.xenos.economylogger.gui;

import dev.xenos.economylogger.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class Gui {

    public final YamlConfiguration cfg;
    public final Text text;
    private final Map<String, Material> pluginIcons = new HashMap<>();

    public Gui(YamlConfiguration cfg, Text text) {
        this.cfg = cfg;
        this.text = text;
        text.load(cfg.getConfigurationSection("theme"));
        ConfigurationSection icons = cfg.getConfigurationSection("entry.plugin-icons");
        if (icons != null) for (String k : icons.getKeys(false)) {
            Material m = Material.matchMaterial(icons.getString(k, ""));
            if (m != null) pluginIcons.put(k.toLowerCase(Locale.ROOT), m);
        }
    }

    public String str(String path, String def) { return cfg.getString(path, def); }
    public List<String> list(String path) { return cfg.getStringList(path); }
    public int num(String path, int def) { return cfg.getInt(path, def); }
    public boolean bool(String path, boolean def) { return cfg.getBoolean(path, def); }

    public Material material(String path, Material def) {
        Material m = Material.matchMaterial(cfg.getString(path, ""));
        return m == null || m.isAir() ? def : m;
    }

    public Material pluginIcon(String plugin) {
        return plugin == null ? null : pluginIcons.get(plugin.toLowerCase(Locale.ROOT));
    }

    public int rows(String path) { return Math.max(3, Math.min(6, cfg.getInt(path, 6))); }

    public List<Integer> slots(String path) { return cfg.getIntegerList(path); }

    public Component title(String path, Map<String, String> ph) { return text.parse(str(path, ""), ph); }

    public String label(String path, String def) { return str("labels." + path, def); }

    public ItemStack button(String path, Material def, Map<String, String> ph) {
        return item(material(path + ".material", def), str(path + ".name", ""), list(path + ".lore"), ph);
    }

    public int slot(String path, int def) { return cfg.getInt(path + ".slot", def); }

    public ItemStack item(Material mat, String name, List<String> lore, Map<String, String> ph) {
        ItemStack it = new ItemStack(mat);
        decorate(it, text.item(name, ph), text.lore(lore, ph));
        return it;
    }

    public static void decorate(ItemStack it, Component name, List<Component> lore) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;
        if (name != null) meta.displayName(name);
        if (lore != null) meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
    }

    public ItemStack pane(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            try {
                meta.setHideTooltip(true);
            } catch (Throwable ignored) {
                meta.displayName(Component.space());
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    public void sound(Player p, String key) {
        if (!bool("sounds.enabled", true)) return;
        String id = str("sounds." + key, null);
        if (id == null || id.isBlank()) return;
        try {
            p.playSound(Sound.sound(Key.key(id), Sound.Source.MASTER,
                    (float) cfg.getDouble("sounds.volume", 0.6), (float) cfg.getDouble("sounds.pitch", 1.4)));
        } catch (Throwable ignored) { }
    }

    public String options(List<String> names, int selected) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append('\n');
            String t = i == selected ? str("log-menu.option-selected", "<g2>» {option}</g2>") : str("log-menu.option-other", "<muted>  {option}");
            sb.append(t.replace("{option}", Text.esc(names.get(i))));
        }
        return sb.toString();
    }

    public List<String> expand(List<String> lines, String key, String multi) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            if (l.contains("{" + key + "}")) out.addAll(Arrays.asList(multi.split("\n")));
            else out.add(l);
        }
        return out;
    }
}
