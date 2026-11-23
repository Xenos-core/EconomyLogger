package dev.xenos.economylogger;

import dev.xenos.economylogger.command.EcoLogCommand;
import dev.xenos.economylogger.gui.Gui;
import dev.xenos.economylogger.gui.MenuListener;
import dev.xenos.economylogger.hook.EconomyHook;
import dev.xenos.economylogger.hook.EssentialsHook;
import dev.xenos.economylogger.storage.Database;
import dev.xenos.economylogger.track.CallerResolver;
import dev.xenos.economylogger.track.ContextTracker;
import dev.xenos.economylogger.track.ItemTracker;
import dev.xenos.economylogger.track.Recorder;
import dev.xenos.economylogger.util.Formats;
import dev.xenos.economylogger.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public final class EconomyLoggerPlugin extends JavaPlugin implements Listener {

    private volatile Settings settings;
    private volatile Gui gui;
    private volatile Formats formats;
    private volatile YamlConfiguration messages;
    private Database database;
    private Recorder recorder;
    private EconomyHook hook;
    private EssentialsHook essentials;

    @Override
    public void onEnable() {
        loadFiles();
        File dbFile = new File(getDataFolder(), getConfig().getString("storage.file", "transactions.db"));
        database = new Database(dbFile, getLogger(),
                getConfig().getInt("storage.batch-size", 500),
                getConfig().getLong("storage.flush-interval-ms", 1000),
                getConfig().getInt("storage.queue-limit", 200000),
                getConfig().getInt("storage.retention-days", 90));
        try {
            database.open();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not open the SQLite database, EconomyLogger is disabled", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        ContextTracker context = new ContextTracker(this);
        ItemTracker items = new ItemTracker(this);
        recorder = new Recorder(this, new CallerResolver(getClassLoader()), context, items);
        hook = new EconomyHook(this, recorder);
        essentials = new EssentialsHook(this, recorder);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(context, this);
        pm.registerEvents(hook, this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(this, this);

        if (getConfig().getBoolean("hook.enabled", true)) hook.register();
        essentials.tryHook();

        PluginCommand cmd = getCommand("ecolog");
        if (cmd != null) {
            EcoLogCommand ex = new EcoLogCommand(this);
            cmd.setExecutor(ex);
            cmd.setTabCompleter(ex);
        }
        getLogger().info("Watching the economy. Records go to " + dbFile.getName() + ".");
    }

    @Override
    public void onDisable() {
        if (essentials != null) essentials.unhook();
        if (hook != null) hook.unregister();
        if (database != null) database.close();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        if (e.getPlugin().getName().equals("Essentials")) essentials.tryHook();
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        essentials.tryHook();
        getLogger().info("Economy: " + hook.economyName() + " · recorder " + hook.status());
    }

    private void loadFiles() {
        saveDefaultConfig();
        reloadConfig();
        settings = Settings.from(getConfig());
        YamlConfiguration guiCfg = load("gui.yml");
        messages = load("messages.yml");
        gui = new Gui(guiCfg, new Text());
        formats = new Formats(getConfig(), guiCfg.getConfigurationSection("labels.ago"), () -> hook == null ? null : hook.delegate());
    }

    /** Loads a file from the data folder; missing keys fall back to the bundled defaults. */
    private YamlConfiguration load(String name) {
        File f = new File(getDataFolder(), name);
        if (!f.exists()) saveResource(name, false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        var in = getResource(name);
        if (in != null) {
            y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        return y;
    }

    public void reload() {
        loadFiles();
        if (essentials != null) {
            if (settings.essentials()) essentials.tryHook(); else essentials.unhook();
        }
    }

    public Settings settings() { return settings; }
    public Gui gui() { return gui; }
    public Formats formats() { return formats; }
    public YamlConfiguration messages() { return messages; }
    public Database database() { return database; }
    public Recorder recorder() { return recorder; }
    public EconomyHook hook() { return hook; }
}
