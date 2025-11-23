package eternalpolar.spigot.eternaloptimization.Utils;

import eternalpolar.spigot.eternaloptimization.EternalOptimization;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitScheduler;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class PerformanceMonitor {

    private final EternalOptimization plugin;
    private final Runtime runtime;
    private BukkitTask memoryCheckTask;
    private BukkitTask debugTask;

    private int memoryCheckInterval;
    private boolean enableMemoryLogging;
    private String memoryLogFormat;
    private boolean debugMode;
    private int debugInterval;

    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private long startTime;

    // 内存基准线相关
    private double baseMemoryUsage = 0.0;
    private boolean baselineSet = false;
    private long warmupPeriod;

    public PerformanceMonitor(EternalOptimization plugin) {
        this.plugin = plugin;
        this.runtime = Runtime.getRuntime();
        this.startTime = System.currentTimeMillis();

        loadConfig();
        startMonitoring();
        setBaseline(); // 立即设置内存基准线
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        this.debugMode = config.getBoolean("debug", false);
        this.debugInterval = config.getInt("debug-interval", 300);

        this.memoryCheckInterval = config.getInt("memory-check-interval", 5);
        this.enableMemoryLogging = config.getBoolean("enable-memory-logging", false);
        this.memoryLogFormat = config.getString("memory-log-format",
                "[EternalOptimization] Memory Monitor: Used=%.2f/%.2f MB (%.1f%%)");

        this.warmupPeriod = config.getLong("performance-tracking.warmup-period", 60) * 1000;
    }

    public long getUsedMemory() {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public long getMaxMemory() {
        return runtime.maxMemory();
    }

    public double getMemoryUsagePercent() {
        return (double) getUsedMemory() / getMaxMemory() * 100;
    }

    public void startMonitoring() {
        stopMonitoring();

        if (enableMemoryLogging) {
            this.memoryCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                logPerformanceStats();
            }, 0, memoryCheckInterval * 20);
        }

        if (debugMode) {
            this.debugTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                logDebugInfo();
            }, 0, debugInterval * 20);
        }
    }

    public void stopMonitoring() {
        if (memoryCheckTask != null) {
            memoryCheckTask.cancel();
            memoryCheckTask = null;
        }

        if (debugTask != null) {
            debugTask.cancel();
            debugTask = null;
        }
    }

    private void logPerformanceStats() {
        if (!enableMemoryLogging) return;

        long usedMemory = getUsedMemory();
        long maxMemory = getMaxMemory();
        double usagePercent = getMemoryUsagePercent();

        String message = String.format(
                memoryLogFormat,
                usedMemory / (1024.0 * 1024.0),
                maxMemory / (1024.0 * 1024.0),
                usagePercent
        );

        Bukkit.getConsoleSender().sendMessage(message);
    }

    private void logDebugInfo() {
        if (!debugMode) return;

        long usedMemory = getUsedMemory();
        long maxMemory = getMaxMemory();
        double usagePercent = getMemoryUsagePercent();
        double memoryImprovement = getMemoryImprovement();

        StringBuilder sb = new StringBuilder();
        sb.append("[EternalOptimization DEBUG] ");
        sb.append("Memory: ").append(decimalFormat.format(usedMemory / (1024.0 * 1024.0)));
        sb.append("/").append(decimalFormat.format(maxMemory / (1024.0 * 1024.0))).append(" MB (");
        sb.append(decimalFormat.format(usagePercent)).append("%) | ");
        sb.append("Base Memory: ").append(decimalFormat.format(baseMemoryUsage)).append(" MB | ");
        sb.append("Memory Improvement: ").append(decimalFormat.format(memoryImprovement)).append("% | ");
        sb.append("Uptime: ").append(formatTime(getUptime()));

        plugin.getLogger().info(sb.toString());
    }

    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private void setBaseline() {
        if (baselineSet) return;

        baseMemoryUsage = getUsedMemory() / (1024.0 * 1024.0);
        baselineSet = true;

        plugin.getLogger().info("[EternalOptimization] Memory baseline set:");
        plugin.getLogger().info("  Base Memory: " + decimalFormat.format(baseMemoryUsage) + " MB");
    }

    public double getMemoryImprovement() {
        if (!baselineSet || baseMemoryUsage == 0) {
            return 0.0;
        }

        double currentMemory = getUsedMemory() / (1024.0 * 1024.0);
        double improvement = ((baseMemoryUsage - currentMemory) / baseMemoryUsage) * 100;
        return Math.max(0.0, improvement);
    }

    public long getUptime() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isMemoryLoggingEnabled() {
        return enableMemoryLogging;
    }

    public boolean isBaselineSet() {
        return baselineSet;
    }
}