package eternalpolar.spigot.eternaloptimization.Module.Load;

import eternalpolar.spigot.eternaloptimization.EternalOptimization;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChuckLoadOptimizated implements Listener {

    private final EternalOptimization plugin;
    private final Map<String, Map<Long, Long>> chunkLoadTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> maxLoadedChunks = new ConcurrentHashMap<>();
    private final Map<String, Integer> playerUnloadRadius = new ConcurrentHashMap<>();
    private final Map<String, Integer> baseUnloadDelay = new ConcurrentHashMap<>();
    private final Map<String, Boolean> worldEnabled = new ConcurrentHashMap<>();
    private int version;
    private int globalMaxLoaded;
    private int globalRadius;
    private int globalDelay;
    private boolean globallyEnabled;
    private long totalChunksOptimized = 0;
    private long totalChunksLoaded = 0;

    public ChuckLoadOptimizated(EternalOptimization plugin) {
        this.plugin = plugin;
        loadConfig();
        if (globallyEnabled) {
            detectVersion();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startTasks();
        }
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "functions/chuckLoad.yml");
        if (!configFile.exists()) {
            plugin.saveResource("functions/chuckLoad.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        globallyEnabled = config.getBoolean("enabled", true);
        if (!globallyEnabled) return;

        globalMaxLoaded = config.getInt("global-world.max-loaded-chunks", 500);
        globalRadius = config.getInt("global-world.player-unload-radius", 3);
        globalDelay = config.getInt("global-world.base-unload-delay", 600);

        boolean globalWorldEnabled = config.getBoolean("global-world.enabled", true);

        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            boolean enabled = config.getBoolean("world-specific." + worldName + ".enabled", globalWorldEnabled);

            worldEnabled.put(worldName, enabled);

            if (enabled) {
                maxLoadedChunks.put(worldName, config.getInt("world-specific." + worldName + ".max-loaded-chunks", globalMaxLoaded));
                playerUnloadRadius.put(worldName, config.getInt("world-specific." + worldName + ".player-unload-radius", globalRadius));
                baseUnloadDelay.put(worldName, config.getInt("world-specific." + worldName + ".base-unload-delay", globalDelay));
            }
        }
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

    private void startTasks() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkChunkUnloadsAsync, 40, 40);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!globallyEnabled) return;

        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();

        if (!isWorldEnabled(worldName)) return;

        long chunkKey = getChunkKey(chunk);
        totalChunksLoaded++;

        chunkLoadTimes.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(chunkKey, System.currentTimeMillis());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> checkMaxLoadedChunksAsync(chunk.getWorld()));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!globallyEnabled) return;

        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();

        if (!isWorldEnabled(worldName)) return;

        long chunkKey = getChunkKey(chunk);

        chunkLoadTimes.getOrDefault(worldName, Collections.emptyMap()).remove(chunkKey);
    }

    private void checkChunkUnloadsAsync() {
        if (!globallyEnabled) return;

        long currentTime = System.currentTimeMillis();
        Set<String> processedWorlds = ConcurrentHashMap.newKeySet();

        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            String worldName = world.getName();

            if (!isWorldEnabled(worldName)) continue;
            if (processedWorlds.contains(worldName)) continue;

            processedWorlds.add(worldName);

            Map<Long, Long> chunkLoadTimes = this.chunkLoadTimes.getOrDefault(worldName, null);
            int unloadDelay = baseUnloadDelay.getOrDefault(worldName, globalDelay);
            int radius = playerUnloadRadius.getOrDefault(worldName, globalRadius);

            if (chunkLoadTimes == null) continue;

            Set<Long> playerChunks = new HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(world)) {
                    int x = p.getLocation().getBlockX() >> 4;
                    int z = p.getLocation().getBlockZ() >> 4;

                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            playerChunks.add(getChunkKey(x + dx, z + dz));
                        }
                    }
                }
            }

            List<Long> chunksToUnload = new ArrayList<>();
            for (Map.Entry<Long, Long> entry : chunkLoadTimes.entrySet()) {
                long chunkKey = entry.getKey();
                long loadTime = entry.getValue();

                if (!playerChunks.contains(chunkKey) && currentTime - loadTime > unloadDelay * 50) {
                    chunksToUnload.add(chunkKey);
                }
            }

            if (!chunksToUnload.isEmpty()) {
                totalChunksOptimized += chunksToUnload.size();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (long chunkKey : chunksToUnload) {
                        int x = (int) (chunkKey >> 32);
                        int z = (int) (chunkKey & 0xFFFFFFFFL);

                        Chunk chunk = world.getChunkAt(x, z);
                        if (chunk.isLoaded() && !isChunkInUse(chunk)) {
                            chunk.unload(true);
                            chunkLoadTimes.remove(chunkKey);
                        }
                    }
                });
            }
        }
    }

    private void checkMaxLoadedChunksAsync(World world) {
        if (!globallyEnabled) return;

        String worldName = world.getName();

        if (!isWorldEnabled(worldName)) return;

        int maxLoaded = maxLoadedChunks.getOrDefault(worldName, globalMaxLoaded);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Chunk[] loadedChunks = world.getLoadedChunks();
            if (loadedChunks.length <= maxLoaded) return;

            int chunksToUnload = loadedChunks.length - maxLoaded;
            Map<Long, Long> chunkLoadTimes = this.chunkLoadTimes.getOrDefault(worldName, null);

            if (chunkLoadTimes == null) return;

            List<Map.Entry<Long, Long>> chunksWithTimes = new ArrayList<>(chunkLoadTimes.entrySet());
            chunksWithTimes.sort(Map.Entry.comparingByValue());

            int radius = playerUnloadRadius.getOrDefault(worldName, globalRadius);
            Set<Long> playerChunks = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(world)) {
                    int x = player.getLocation().getBlockX() >> 4;
                    int z = player.getLocation().getBlockZ() >> 4;

                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            playerChunks.add(getChunkKey(x + dx, z + dz));
                        }
                    }
                }
            }

            int unloadedCount = 0;
            for (Map.Entry<Long, Long> entry : chunksWithTimes) {
                if (unloadedCount >= chunksToUnload) break;

                long chunkKey = entry.getKey();

                if (playerChunks.contains(chunkKey)) continue;

                int x = (int) (chunkKey >> 32);
                int z = (int) (chunkKey & 0xFFFFFFFFL);

                Chunk chunk = world.getChunkAt(x, z);
                if (chunk.isLoaded() && !isChunkInUse(chunk)) {
                    chunk.unload(true);
                    chunkLoadTimes.remove(chunkKey);
                    unloadedCount++;
                }
            }
            totalChunksOptimized += unloadedCount;
        });
    }

    private boolean isChunkInUse(Chunk chunk) {
        if (version >= 13) {
            try {
                return (boolean) Chunk.class.getMethod("isForceLoaded").invoke(chunk);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private boolean isWorldEnabled(String worldName) {
        return worldEnabled.getOrDefault(worldName, false);
    }

    public long getChunkKey(Chunk chunk) {
        return getChunkKey(chunk.getX(), chunk.getZ());
    }

    public long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public int getVersion() {
        return version;
    }

    public boolean isEnabled() {
        return globallyEnabled;
    }

    public void disable() {
        chunkLoadTimes.clear();
        worldEnabled.clear();
    }

    public double getOptimizationStats() {
        if (totalChunksLoaded == 0) return 0.0;
        return (totalChunksOptimized / (double) totalChunksLoaded) * 100;
    }
}