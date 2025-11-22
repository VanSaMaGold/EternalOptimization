package eternalpolar.spigot.eternaloptimization.Utils;

import eternalpolar.spigot.eternaloptimization.EternalOptimization;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

public class MemoryMonitor {

    private final EternalOptimization plugin;
    private final Runtime runtime;
    private int memoryCheckTaskId;
    private long lastMemoryUsage;
    private long currentMemorySaved;
    private int memoryCheckInterval;

    public MemoryMonitor(EternalOptimization plugin) {
        this.plugin = plugin;
        this.runtime = Runtime.getRuntime();
        this.lastMemoryUsage = getUsedMemory();
        this.currentMemorySaved = 0;

        FileConfiguration config = plugin.getConfig();
        this.memoryCheckInterval = config.getInt("memory-check-interval", 60);
    }

    public long getUsedMemory() {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public long getMaxMemory() {
        return runtime.maxMemory();
    }

    public long getFreeMemory() {
        return runtime.freeMemory();
    }

    public double getMemoryUsagePercent() {
        return (double) getUsedMemory() / getMaxMemory() * 100;
    }

    public void startMemoryMonitoring() {
        if (memoryCheckTaskId != 0) {
            Bukkit.getScheduler().cancelTask(memoryCheckTaskId);
        }

        memoryCheckTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentMemory = getUsedMemory();
            long memoryDiff = lastMemoryUsage - currentMemory;

            if (memoryDiff > 0) {
                currentMemorySaved += memoryDiff;
            } else {
                currentMemorySaved = 0;
            }

            lastMemoryUsage = currentMemory;

            logMemoryStats();
        }, 0, memoryCheckInterval * 20).getTaskId();
    }

    public void stopMemoryMonitoring() {
        if (memoryCheckTaskId != 0) {
            Bukkit.getScheduler().cancelTask(memoryCheckTaskId);
            memoryCheckTaskId = 0;
        }
    }

    private void logMemoryStats() {
        FileConfiguration config = plugin.getConfig();
        boolean enableMemoryLogging = config.getBoolean("enable-memory-logging", true);

        if (!enableMemoryLogging) {
            return;
        }

        long usedMemory = getUsedMemory();
        long maxMemory = getMaxMemory();
        double usagePercent = getMemoryUsagePercent();
        double savedMB = currentMemorySaved / (1024.0 * 1024.0);

        String logFormat = config.getString("memory-log-format",
                "[EternalOptimization] Memory: %.2f/%.2f MB (%.1f%%) | Saved: %.2f MB");

        String message = String.format(
                logFormat,
                usedMemory / (1024.0 * 1024.0),
                maxMemory / (1024.0 * 1024.0),
                usagePercent,
                savedMB
        );

        Bukkit.getConsoleSender().sendMessage(message);
    }

    public long getCurrentMemorySaved() {
        return currentMemorySaved;
    }

    public void resetMemorySaved() {
        currentMemorySaved = 0;
    }
}
