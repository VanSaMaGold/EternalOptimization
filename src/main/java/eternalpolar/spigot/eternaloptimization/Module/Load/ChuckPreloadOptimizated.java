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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ChuckPreloadOptimizated implements Listener {

    private final EternalOptimization plugin;
    private final Map<String, Map<Long, Long>> preloadedChunks = new ConcurrentHashMap<>();
    private final Map<String, Integer> preloadRadius = new ConcurrentHashMap<>();
    private final Map<String, Integer> preloadDelay = new ConcurrentHashMap<>();
    private final Map<String, Integer> maxPreloadedChunks = new ConcurrentHashMap<>();
    private final Map<String, Boolean> worldEnabled = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> cleanupTasks = new ConcurrentHashMap<>();
    private final int bukkitVersion;

    private boolean globallyEnabled;
    private int globalPreloadRadius;
    private int globalPreloadDelay;
    private int globalMaxPreloaded;
    private boolean loadAsync;
    private int cleanupInterval;

    public ChuckPreloadOptimizated(EternalOptimization plugin) {
        this.plugin = plugin;
        this.bukkitVersion = detectBukkitVersion();
        loadConfig();
        if (globallyEnabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startCleanupTasks();
            plugin.getLogger().info("Chunk Preload Optimization enabled successfully!");
        } else {
            plugin.getLogger().info("Chunk Preload Optimization is disabled in configuration.");
        }
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "functions/chuckPreload.yml");
        if (!configFile.exists()) {
            plugin.saveResource("functions/chuckPreload.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        globallyEnabled = config.getBoolean("enabled", true);
        if (!globallyEnabled) return;

        globalPreloadRadius = config.getInt("global-world.preload-radius", 2);
        globalPreloadDelay = config.getInt("global-world.preload-delay-ticks", 10);
        globalMaxPreloaded = config.getInt("global-world.max-preloaded-chunks", 200);
        loadAsync = config.getBoolean("load-async", true);
        cleanupInterval = config.getInt("cleanup-interval-seconds", 30);

        boolean globalWorldEnabled = config.getBoolean("global-world.enabled", true);

        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            boolean enabled = config.getBoolean("world-specific." + worldName + ".enabled", globalWorldEnabled);

            worldEnabled.put(worldName, enabled);

            if (enabled) {
                preloadRadius.put(worldName, config.getInt("world-specific." + worldName + ".preload-radius", globalPreloadRadius));
                preloadDelay.put(worldName, config.getInt("world-specific." + worldName + ".preload-delay-ticks", globalPreloadDelay));
                maxPreloadedChunks.put(worldName, config.getInt("world-specific." + worldName + ".max-preloaded-chunks", globalMaxPreloaded));
            }
        }
    }

    private int detectBukkitVersion() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        if (bukkitVersion.startsWith("1.8")) return 8;
        if (bukkitVersion.startsWith("1.9")) return 9;
        if (bukkitVersion.startsWith("1.10")) return 10;
        if (bukkitVersion.startsWith("1.11")) return 11;
        if (bukkitVersion.startsWith("1.12")) return 12;
        if (bukkitVersion.startsWith("1.13")) return 13;
        if (bukkitVersion.startsWith("1.14")) return 14;
        if (bukkitVersion.startsWith("1.15")) return 15;
        if (bukkitVersion.startsWith("1.16")) return 16;
        if (bukkitVersion.startsWith("1.17")) return 17;
        if (bukkitVersion.startsWith("1.18")) return 18;
        if (bukkitVersion.startsWith("1.19")) return 19;
        if (bukkitVersion.startsWith("1.20")) return 20;
        if (bukkitVersion.startsWith("1.21")) return 21;
        return 12;
    }

    private void startCleanupTasks() {
        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            if (isWorldEnabled(worldName)) {
                startWorldCleanupTask(worldName);
            }
        }
    }

    private void startWorldCleanupTask(String worldName) {
        if (cleanupTasks.containsKey(worldName)) {
            cleanupTasks.get(worldName).cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null || !isWorldEnabled(worldName)) return;

            Map<Long, Long> chunks = preloadedChunks.getOrDefault(worldName, Collections.emptyMap());
            if (chunks.isEmpty()) return;

            long currentTime = System.currentTimeMillis();
            int maxAge = cleanupInterval * 1000;
            List<Long> chunksToRemove = new ArrayList<>();

            for (Map.Entry<Long, Long> entry : chunks.entrySet()) {
                if (currentTime - entry.getValue() > maxAge) {
                    chunksToRemove.add(entry.getKey());
                }
            }

            if (!chunksToRemove.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (long chunkKey : chunksToRemove) {
                        int x = (int) (chunkKey >> 32);
                        int z = (int) (chunkKey & 0xFFFFFFFFL);
                        Chunk chunk = world.getChunkAt(x, z);

                        if (chunk.isLoaded() && !isChunkInUse(world, x, z)) {
                            unloadChunk(chunk);
                        }
                        chunks.remove(chunkKey);
                    }
                });
            }
        }, 0, cleanupInterval * 20);

        cleanupTasks.put(worldName, task);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!globallyEnabled) return;

        Player player = event.getPlayer();
        World world = player.getWorld();
        String worldName = world.getName();

        if (!isWorldEnabled(worldName) || !isSignificantMove(event)) return;

        schedulePreload(player, worldName);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!globallyEnabled) return;

        Player player = event.getPlayer();
        World world = player.getWorld();
        String worldName = world.getName();

        if (!isWorldEnabled(worldName)) return;

        schedulePreload(player, worldName);
    }

    private boolean isSignificantMove(PlayerMoveEvent event) {
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();
        return fromChunk.getX() != toChunk.getX() || fromChunk.getZ() != toChunk.getZ();
    }

    private void schedulePreload(Player player, String worldName) {
        int delay = preloadDelay.getOrDefault(worldName, globalPreloadDelay);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getWorld().getName().equals(worldName)) {
                preloadChunks(player, worldName);
            }
        }, delay);
    }

    private void preloadChunks(Player player, String worldName) {
        World world = player.getWorld();
        int radius = preloadRadius.getOrDefault(worldName, globalPreloadRadius);
        int maxPreloaded = maxPreloadedChunks.getOrDefault(worldName, globalMaxPreloaded);

        int playerX = player.getLocation().getBlockX() >> 4;
        int playerZ = player.getLocation().getBlockZ() >> 4;

        Map<Long, Long> chunks = preloadedChunks.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
        List<Long> chunksToLoad = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = playerX + dx;
                int z = playerZ + dz;
                long chunkKey = getChunkKey(x, z);

                if (!chunks.containsKey(chunkKey) && !isChunkLoaded(world, x, z)) {
                    chunksToLoad.add(chunkKey);
                }
            }
        }

        if (chunks.size() + chunksToLoad.size() > maxPreloaded) {
            int excess = (chunks.size() + chunksToLoad.size()) - maxPreloaded;
            removeOldestChunks(worldName, excess);
        }

        if (loadAsync) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                for (long chunkKey : chunksToLoad) {
                    int x = (int) (chunkKey >> 32);
                    int z = (int) (chunkKey & 0xFFFFFFFFL);
                    loadChunkAsync(world, x, z, chunkKey, worldName);
                }
            });
        } else {
            for (long chunkKey : chunksToLoad) {
                int x = (int) (chunkKey >> 32);
                int z = (int) (chunkKey & 0xFFFFFFFFL);
                loadChunkSync(world, x, z, chunkKey, worldName);
            }
        }
    }

    private void loadChunkSync(World world, int x, int z, long chunkKey, String worldName) {
        if (!isChunkLoaded(world, x, z)) {
            loadChunk(world, x, z);
            preloadedChunks.getOrDefault(worldName, new ConcurrentHashMap<>()).put(chunkKey, System.currentTimeMillis());
        }
    }

    private void loadChunkAsync(World world, int x, int z, long chunkKey, String worldName) {
        try {
            Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                if (isChunkLoaded(world, x, z)) return false;
                loadChunk(world, x, z);
                preloadedChunks.getOrDefault(worldName, new ConcurrentHashMap<>()).put(chunkKey, System.currentTimeMillis());
                return true;
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to preload chunk (" + x + "," + z + ") in world " + worldName + ": " + e.getMessage());
        }
    }

    private boolean isChunkLoaded(World world, int x, int z) {
        try {
            return (boolean) World.class.getMethod("isChunkLoaded", int.class, int.class).invoke(world, x, z);
        } catch (Exception e) {
            return world.isChunkLoaded(x, z);
        }
    }

    private boolean isChunkInUse(World world, int x, int z) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)) {
                Chunk playerChunk = player.getLocation().getChunk();
                if (playerChunk.getX() == x && playerChunk.getZ() == z) {
                    return true;
                }
            }
        }
        return isChunkForceLoaded(world, x, z);
    }

    private boolean isChunkForceLoaded(World world, int x, int z) {
        if (bukkitVersion >= 13) {
            try {
                return (boolean) World.class.getMethod("isChunkForceLoaded", int.class, int.class).invoke(world, x, z);
            } catch (Exception e) {
                try {
                    Chunk chunk = world.getChunkAt(x, z);
                    return (boolean) Chunk.class.getMethod("isForceLoaded").invoke(chunk);
                } catch (Exception ex) {
                    return false;
                }
            }
        } else {
            try {
                return (boolean) World.class.getMethod("isChunkForceLoaded", int.class, int.class).invoke(world, x, z);
            } catch (Exception e) {
                return false;
            }
        }
    }

    private void loadChunk(World world, int x, int z) {
        try {
            if (bukkitVersion >= 13) {
                World.class.getMethod("loadChunk", int.class, int.class, boolean.class).invoke(world, x, z, false);
            } else {
                World.class.getMethod("loadChunk", int.class, int.class).invoke(world, x, z);
            }
        } catch (Exception e) {
            world.loadChunk(x, z);
        }
    }

    private void unloadChunk(Chunk chunk) {
        try {
            if (bukkitVersion >= 13) {
                Chunk.class.getMethod("unload", boolean.class).invoke(chunk, false);
            } else {
                Chunk.class.getMethod("unload").invoke(chunk);
            }
        } catch (Exception e) {
            chunk.unload();
        }
    }

    private void removeOldestChunks(String worldName, int count) {
        Map<Long, Long> chunks = preloadedChunks.getOrDefault(worldName, Collections.emptyMap());
        if (chunks.isEmpty() || count <= 0) return;

        List<Map.Entry<Long, Long>> sortedChunks = new ArrayList<>(chunks.entrySet());
        sortedChunks.sort(Map.Entry.comparingByValue());

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        int removed = 0;
        for (Map.Entry<Long, Long> entry : sortedChunks) {
            if (removed >= count) break;

            long chunkKey = entry.getKey();
            int x = (int) (chunkKey >> 32);
            int z = (int) (chunkKey & 0xFFFFFFFFL);
            Chunk chunk = world.getChunkAt(x, z);

            if (chunk.isLoaded() && !isChunkInUse(world, x, z)) {
                unloadChunk(chunk);
                chunks.remove(chunkKey);
                removed++;
            }
        }
    }

    private boolean isWorldEnabled(String worldName) {
        return worldEnabled.getOrDefault(worldName, false);
    }

    public long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public boolean isEnabled() {
        return globallyEnabled;
    }

    public void disable() {
        preloadedChunks.clear();
        worldEnabled.clear();
        cleanupTasks.values().forEach(BukkitTask::cancel);
        cleanupTasks.clear();
    }

    public void reloadConfig() {
        disable();
        loadConfig();
        if (globallyEnabled) {
            startCleanupTasks();
        }
    }

    public int getPreloadedChunkCount(String worldName) {
        return preloadedChunks.getOrDefault(worldName, Collections.emptyMap()).size();
    }

    public int getTotalPreloadedChunks() {
        int total = 0;
        for (Map<Long, Long> chunks : preloadedChunks.values()) {
            total += chunks.size();
        }
        return total;
    }

    public int getBukkitVersion() {
        return bukkitVersion;
    }
}