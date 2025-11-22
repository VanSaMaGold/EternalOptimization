package eternalpolar.spigot.eternaloptimization.Module.Chuck;

import eternalpolar.spigot.eternaloptimization.EternalOptimization;
import eternalpolar.spigot.eternaloptimization.Module.Chuck.Model.ChunkLoadedA;
import eternalpolar.spigot.eternaloptimization.Module.Chuck.Model.ChunkLoadedB;
import eternalpolar.spigot.eternaloptimization.Module.Chuck.Model.ChunkLoadedC;
import eternalpolar.spigot.eternaloptimization.Module.Chuck.Model.ChunkLoadedD;
import eternalpolar.spigot.eternaloptimization.Utils.MemoryMonitor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkManager implements Listener {

    private final EternalOptimization plugin;
    private final MemoryMonitor memoryMonitor;
    private final Map<String, ChunkOptimizationStrategy> strategies = new HashMap<>();
    private final Map<String, Map<Long, Long>> chunkLoadTimes = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Boolean>> optimizedChunks = new ConcurrentHashMap<>();
    private int version;
    private boolean debugMode;
    private int totalOptimizedChunks;
    private FileConfiguration chunkConfig;

    public ChunkManager(EternalOptimization plugin, MemoryMonitor memoryMonitor) {
        this.plugin = plugin;
        this.memoryMonitor = memoryMonitor;
        this.debugMode = plugin.getConfig().getBoolean("debug", false);
        this.totalOptimizedChunks = 0;

        loadConfig();
        detectVersion();
        registerStrategies();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startTasks();

        logDebug("ChunkManager initialized. Debug mode: " + debugMode);
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "functions/chuck.yml");
        if (!configFile.exists()) {
            plugin.saveResource("functions/chuck.yml", false);
        }
        this.chunkConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    private void detectVersion() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        if (bukkitVersion.startsWith("1.8")) {
            version = 8;
        } else if (bukkitVersion.startsWith("1.9")) {
            version = 9;
        } else if (bukkitVersion.startsWith("1.10")) {
            version = 10;
        } else if (bukkitVersion.startsWith("1.11")) {
            version = 11;
        } else if (bukkitVersion.startsWith("1.12")) {
            version = 12;
        } else if (bukkitVersion.startsWith("1.13")) {
            version = 13;
        } else if (bukkitVersion.startsWith("1.14")) {
            version = 14;
        } else if (bukkitVersion.startsWith("1.15")) {
            version = 15;
        } else if (bukkitVersion.startsWith("1.16")) {
            version = 16;
        } else if (bukkitVersion.startsWith("1.17")) {
            version = 17;
        } else if (bukkitVersion.startsWith("1.18")) {
            version = 18;
        } else if (bukkitVersion.startsWith("1.19")) {
            version = 19;
        } else if (bukkitVersion.startsWith("1.20")) {
            version = 20;
        } else if (bukkitVersion.startsWith("1.21")) {
            version = 21;
        } else {
            version = 12;
        }
    }

    private void registerStrategies() {
        if (chunkConfig.getBoolean("optimization-strategies.strategyA.enabled")) {
            strategies.put("A", new ChunkLoadedA(this));
            logDebug("Strategy A registered");
        }
        if (chunkConfig.getBoolean("optimization-strategies.strategyB.enabled")) {
            strategies.put("B", new ChunkLoadedB(this));
            logDebug("Strategy B registered");
        }
        if (chunkConfig.getBoolean("optimization-strategies.strategyC.enabled")) {
            strategies.put("C", new ChunkLoadedC(this));
            logDebug("Strategy C registered");
        }
        if (chunkConfig.getBoolean("optimization-strategies.strategyD.enabled")) {
            strategies.put("D", new ChunkLoadedD(this));
            logDebug("Strategy D registered");
        }
    }

    private void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (ChunkOptimizationStrategy strategy : strategies.values()) {
                strategy.tick();
            }
        }, 20, 20);

        if (debugMode) {
            long debugInterval = chunkConfig.getLong("debug-interval", 300) * 20;
            Bukkit.getScheduler().runTaskTimer(plugin, this::printDebugInfo, debugInterval, debugInterval);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        long chunkKey = getChunkKey(chunk);

        chunkLoadTimes.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(chunkKey, System.currentTimeMillis());

        strategies.values().forEach(strategy -> strategy.onChunkLoad(event));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        long chunkKey = getChunkKey(chunk);

        chunkLoadTimes.getOrDefault(worldName, Collections.emptyMap()).remove(chunkKey);
        optimizedChunks.getOrDefault(worldName, Collections.emptyMap()).remove(chunkKey);

        strategies.values().forEach(strategy -> strategy.onChunkUnload(event));

        markChunkAsOptimized(chunk);
    }

    public void markChunkAsOptimized(Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        long chunkKey = getChunkKey(chunk);

        optimizedChunks.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .putIfAbsent(chunkKey, true);

        totalOptimizedChunks++;

        if (debugMode && totalOptimizedChunks % 10 == 0) {
            logDebug("Optimized chunks count: " + totalOptimizedChunks);
        }
    }

    private void printDebugInfo() {
        long currentMemory = memoryMonitor.getUsedMemory();
        long memorySaved = memoryMonitor.getCurrentMemorySaved();

        int loadedChunks = 0;
        for (World world : Bukkit.getWorlds()) {
            loadedChunks += world.getLoadedChunks().length;
        }

        String message = String.format(
                "[EternalOptimization] Debug Info - Loaded: %d, Optimized: %d, Memory Saved: %.2f MB, Current Memory: %.2f MB",
                loadedChunks,
                totalOptimizedChunks,
                memorySaved / (1024.0 * 1024.0),
                currentMemory / (1024.0 * 1024.0)
        );

        Bukkit.getConsoleSender().sendMessage(message);
    }

    public void logDebug(String message) {
        if (debugMode) {
            Bukkit.getConsoleSender().sendMessage("[EternalOptimization-Debug] " + message);
        }
    }

    public long getChunkKey(Chunk chunk) {
        return ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
    }

    public int getVersion() {
        return version;
    }

    public FileConfiguration getConfig() {
        return chunkConfig;
    }

    public Map<String, Map<Long, Long>> getChunkLoadTimes() {
        return chunkLoadTimes;
    }

    public EternalOptimization getPlugin() {
        return plugin;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void disable() {
        strategies.values().forEach(ChunkOptimizationStrategy::onDisable);
        logDebug("ChunkManager disabled");
    }
}
