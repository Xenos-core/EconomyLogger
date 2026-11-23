package dev.xenos.economylogger.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MiniMessage with the plugin's theme tags and {placeholder} support. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern PH = Pattern.compile("\\{([a-z_]+)}");

    private String gradient = "<gradient:#B6FFCF:#3DDC84:#0FA958>";
    private String accent = "<gradient:#E3FF8A:#7BE495>";
    private String income = "#5BE49B";
    private String spend = "#FF7A7A";
    private String muted = "#8BA698";
    private String text = "#EAFBF1";

    public void load(ConfigurationSection theme) {
        if (theme == null) return;
        gradient = grad(theme.getStringList("gradient"), gradient);
        accent = grad(theme.getStringList("accent"), accent);
        income = theme.getString("income", income);
        spend = theme.getString("spend", spend);
        muted = theme.getString("muted", muted);
        text = theme.getString("text", text);
    }

    private static String grad(List<String> colors, String def) {
        if (colors == null || colors.isEmpty()) return def;
        if (colors.size() == 1) return "<color:" + colors.get(0) + ">";
        return "<gradient:" + String.join(":", colors) + ">";
    }

    public String income() { return income; }
    public String spend() { return spend; }

    private String theme(String s) {
        if (s.indexOf('<') < 0) return s;
        String closeG = gradient.startsWith("<color") ? "</color>" : "</gradient>";
        String closeA = accent.startsWith("<color") ? "</color>" : "</gradient>";
        return s.replace("<g>", gradient).replace("</g>", closeG)
                .replace("<g2>", accent).replace("</g2>", closeA)
                .replace("<in>", "<color:" + income + ">").replace("</in>", "</color>")
                .replace("<out>", "<color:" + spend + ">").replace("</out>", "</color>")
                .replace("<muted>", "<color:" + muted + ">").replace("</muted>", "</color>")
                .replace("<text>", "<color:" + text + ">").replace("</text>", "</color>");
    }

    /** Replaces {placeholders}; values are inserted raw (callers escape untrusted text with {@link #esc}). */
    public static String fill(String s, Map<String, String> ph) {
        if (ph == null || ph.isEmpty() || s.indexOf('{') < 0) return s;
        Matcher m = PH.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = ph.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group() : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public Component parse(String s, Map<String, String> ph) {
        return MM.deserialize(theme(fill(s, ph)));
    }

    /** For item names and lore: no default italics. */
    public Component item(String s, Map<String, String> ph) {
        return parse(s, ph).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Lore lines; a line whose placeholder has an empty value is dropped. */
    public List<Component> lore(List<String> lines, Map<String, String> ph) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (ph != null && hasEmpty(line, ph)) continue;
            out.add(item(line, ph));
        }
        return out;
    }

    private static boolean hasEmpty(String line, Map<String, String> ph) {
        Matcher m = PH.matcher(line);
        while (m.find()) {
            String v = ph.get(m.group(1));
            if (v != null && v.isEmpty()) return true;
        }
        return false;
    }

    public static String esc(String s) {
        return s == null ? "" : MM.escapeTags(s);
    }

    public static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }
}
