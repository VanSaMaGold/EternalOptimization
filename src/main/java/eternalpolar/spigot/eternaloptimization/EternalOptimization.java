package eternalpolar.spigot.eternaloptimization;

import eternalpolar.spigot.eternaloptimization.Commands.CMD;
import eternalpolar.spigot.eternaloptimization.Commands.EOCommandCompleter;
import eternalpolar.spigot.eternaloptimization.Module.Load.ChuckLoadOptimizated;
import eternalpolar.spigot.eternaloptimization.Module.Load.ChuckPreloadOptimizated;
import eternalpolar.spigot.eternaloptimization.Module.Other.PacketsReceiveOptimizated;
import eternalpolar.spigot.eternaloptimization.Utils.Metrics;
import eternalpolar.spigot.eternaloptimization.Utils.PerformanceMonitor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class EternalOptimization extends JavaPlugin {

    private static EternalOptimization instance;
    private ChuckLoadOptimizated chuckOptimizer;
    private ChuckPreloadOptimizated chuckPreloadOptimizer;
    private PacketsReceiveOptimizated packetOptimizer;
    private PerformanceMonitor performanceMonitor;
    private CMD commandHandler;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.performanceMonitor = new PerformanceMonitor(this);
        this.chuckOptimizer = new ChuckLoadOptimizated(this);
        this.chuckPreloadOptimizer = new ChuckPreloadOptimizated(this);
        this.packetOptimizer = new PacketsReceiveOptimizated(this);
        this.commandHandler = new CMD(this);

        registerCommands();
        performanceMonitor.startMonitoring();
        displayStartupMessage();
    }

    @Override
    public void onDisable() {
        if (chuckOptimizer != null) {
            chuckOptimizer.disable();
        }
        if (chuckPreloadOptimizer != null) {
            chuckPreloadOptimizer.disable();
        }
        if (packetOptimizer != null) {
            packetOptimizer.disable();
        }
        if (performanceMonitor != null) {
            performanceMonitor.stopMonitoring();
        }

        getLogger().info("EternalOptimization disabled.");
        instance = null;
    }

    private void registerCommands() {
        if (getCommand("eternaloptimization") != null) {
            getCommand("eternaloptimization").setExecutor(commandHandler);
            getCommand("eternaloptimization").setTabCompleter(new EOCommandCompleter());
        }
    }

    public ChuckLoadOptimizated getChuckOptimizer() {
        return chuckOptimizer;
    }

    public ChuckPreloadOptimizated getChuckPreloadOptimizer() {
        return chuckPreloadOptimizer;
    }

    public PacketsReceiveOptimizated getPacketOptimizer() {
        return packetOptimizer;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    private void displayStartupMessage() {
        getLogger().info("");
        getLogger().info("");
        sendColoredMessage("| &3Eternal&bOptimization &7has been enabled &7| &fVersion: &a" + getDescription().getVersion());
        sendColoredMessage("| &fAuthor &bEternal &3Polar");
        sendColoredMessage("| &fRunning on " + getServer().getName() + " " + getServer().getVersion());
        getLogger().info("");
        initializeMetrics();
    }

    private void sendColoredMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(
                ChatColor.translateAlternateColorCodes('&',
                        "&7[&3Eternal&bOptimization&7] &r" + message)
        );
    }

    private void initializeMetrics() {
        int pluginId = 28087;
        new Metrics(this, pluginId);
    }

    public static EternalOptimization getInstance() {
        return instance;
    }
}