package dev.xenos.economylogger.track;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class CallerResolver {

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final String NONE = "";

    private final ClassLoader own;
    private final ClassValue<String> names = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            ClassLoader cl = type.getClassLoader();
            if (cl == null || cl == own) return NONE;
            String n = type.getName();
            if (n.startsWith("net.milkbowl.vault.") || n.startsWith("net.milkbowl.vault2.")) return NONE;
            try {
                Plugin p = JavaPlugin.getProvidingPlugin(type);
                String name = p.getName();
                if (name.equalsIgnoreCase("Vault") || name.equalsIgnoreCase("VaultUnlocked")) return NONE;
                return name;
            } catch (Throwable t) {
                return NONE;
            }
        }
    };

    public CallerResolver(ClassLoader own) {
        this.own = own;
    }

    public String caller() {
        return caller(null);
    }

    public String caller(String skip) {
        Optional<String> found = WALKER.walk(s -> s.limit(96)
                .map(f -> names.get(f.getDeclaringClass()))
                .filter(n -> !n.isEmpty() && (skip == null || !n.equalsIgnoreCase(skip)))
                .findFirst());
        return found.orElse(null);
    }
}
