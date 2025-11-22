package eternalpolar.spigot.eternaloptimization;

import eternalpolar.spigot.eternaloptimization.Module.Chuck.ChunkManager;
import eternalpolar.spigot.eternaloptimization.Utils.MemoryMonitor;
import eternalpolar.spigot.eternaloptimization.Utils.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class EternalOptimization extends JavaPlugin {

    private static EternalOptimization instance;
    private ChunkManager chunkManager;
    private MemoryMonitor memoryMonitor;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.memoryMonitor = new MemoryMonitor(this);
        this.chunkManager = new ChunkManager(this, memoryMonitor);

        memoryMonitor.startMemoryMonitoring();
        displayStartupMessage();
    }

    @Override
    public void onDisable() {
        if (chunkManager != null) {
            chunkManager.disable();
        }
        if (memoryMonitor != null) {
            memoryMonitor.stopMemoryMonitoring();
        }

        getLogger().info("EternalOptimization disabled.");
        instance = null;
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public MemoryMonitor getMemoryMonitor() {
        return memoryMonitor;
    }

    private void displayStartupMessage() {
        getLogger().info("");
        getLogger().info("");
        sendColoredMessage("| &3Eternal&bOptimization &7has been enabled &7| &fVersion: &a" + getDescription().getVersion());
        sendColoredMessage("| &fAuthor &bEternal &3Polar");
        sendColoredMessage("| &fRunning on " + getServer().getName() + " " + getServer().getVersion());
        getLogger().info("");
        getLogger().info("");
        sendColoredMessage("| &fQQ &b2047752264 | &aWeChat &bdll764");
        getLogger().info("");
        getLogger().info("");
        initializeMetrics();
    }

    private void initializeMetrics() {
        int pluginId = 28087; // Replace with your plugin's bStats ID
        new Metrics(this, pluginId);
    }

    private void sendColoredMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(
                ChatColor.translateAlternateColorCodes('&',
                        "&7[&3Eternal&bOptimization&7] &r" + message)
        );
    }

    public static EternalOptimization getInstance() {
        return instance;
    }
}
