package my.vulcan.event;

import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private static Main instance;
    private VulcanManager vulcanManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        this.vulcanManager = new VulcanManager(this);
        
        if (getCommand("vulcan") != null) {
            getCommand("vulcan").setExecutor(new VulcanCommand(this));
        }
        getServer().getPluginManager().registerEvents(new MenuManager(this), this);
        
        this.vulcanManager.startAutoEventTimer();
    }

    @Override
    public void onDisable() {
        if (this.vulcanManager != null) {
            this.vulcanManager.stopCurrentEvent();
        }
    }

    public static Main getInstance() { return instance; }
    public VulcanManager getVulcanManager() { return vulcanManager; }
}