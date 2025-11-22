package eternalpolar.spigot.eternaloptimization.Module.Chuck.Model;

import eternalpolar.spigot.eternaloptimization.Module.Chuck.ChunkManager;
import eternalpolar.spigot.eternaloptimization.Module.Chuck.ChunkOptimizationStrategy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkLoadedD implements ChunkOptimizationStrategy, Listener {

    private final ChunkManager chunkManager;
    private final Map<String, WorldConfig> worldConfigs = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, ChunkData>> chunkDataMap = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> playerChunks = new ConcurrentHashMap<>();
    private int onlinePlayers;

    public ChunkLoadedD(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
        Bukkit.getPluginManager().registerEvents(this, chunkManager.getPlugin());

        loadWorldConfigs();
        this.onlinePlayers = Bukkit.getOnlinePlayers().size();

        if (chunkManager.isDebugMode()) {
            chunkManager.logDebug("ChunkLoadedD initialized - World configs: " + worldConfigs.size() +
                    ", Online players: " + onlinePlayers);
        }
    }

    private void loadWorldConfigs() {
        FileConfiguration config = chunkManager.getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("world-specific");

        if (worldsSection != null) {
            for (String worldName : worldsSection.getKeys(false)) {
                ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
                if (worldSection != null) {
                    WorldConfig worldConfig = new WorldConfig();
                    worldConfig.maxLoadedChunks = worldSection.getInt("max-loaded-chunks", 500);
                    worldConfig.baseUnloadDelay = worldSection.getInt("base-unload-delay", 600);
                    worldConfig.playerUnloadDelay = worldSection.getInt("player-unload-delay", 1200);
                    worldConfig.highEntityThreshold = worldSection.getInt("high-entity-threshold", 50);
                    worldConfig.highEntityUnloadDelay = worldSection.getInt("high-entity-unload-delay", 1800);
                    worldConfig.lowEntityThreshold = worldSection.getInt("low-entity-threshold", 10);
                    worldConfig.lowEntityUnloadDelay = worldSection.getInt("low-entity-unload-delay", 300);
                    worldConfig.priorityRadius = worldSection.getInt("priority-radius", 3);

                    worldConfigs.put(worldName, worldConfig);

                    if (chunkManager.isDebugMode()) {
                        chunkManager.logDebug("Loaded config for world: " + worldName +
                                ", Max chunks: " + worldConfig.maxLoadedChunks);
                    }
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            if (!worldConfigs.containsKey(worldName)) {
                WorldConfig defaultConfig = new WorldConfig();
                worldConfigs.put(worldName, defaultConfig);

                if (chunkManager.isDebugMode()) {
                    chunkManager.logDebug("Using default config for world: " + worldName);
                }
            }
        }
    }

    private WorldConfig getWorldConfig(String worldName) {
        return worldConfigs.getOrDefault(worldName, new WorldConfig());
    }

    @Override
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        long chunkKey = chunkManager.getChunkKey(chunk);

        chunkDataMap.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, k -> new ChunkData());

        updateChunkPriority(chunk);

        chunkManager.markChunkAsOptimized(chunk);
    }

    @Override
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        long chunkKey = chunkManager.getChunkKey(chunk);

        chunkDataMap.getOrDefault(worldName, Collections.emptyMap()).remove(chunkKey);
        playerChunks.getOrDefault(worldName, Collections.emptySet()).remove(chunkKey);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        onlinePlayers++;
        updateAllChunkPriorities();

        if (chunkManager.isDebugMode()) {
            chunkManager.logDebug("Player joined - Online: " + onlinePlayers);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        onlinePlayers--;
        updateAllChunkPriorities();

        if (chunkManager.isDebugMode()) {
            chunkManager.logDebug("Player quit - Online: " + onlinePlayers);
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        Chunk chunk = entity.getLocation().getChunk();
        String worldName = chunk.getWorld().getName();
        long chunkKey = chunkManager.getChunkKey(chunk);

        ChunkData chunkData = chunkDataMap.getOrDefault(worldName, Collections.emptyMap()).get(chunkKey);
        if (chunkData != null) {
            chunkData.entityCount++;
            updateChunkPriority(chunk);
        }
    }

    private void updateAllChunkPriorities() {
        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            for (Chunk chunk : world.getLoadedChunks()) {
                updateChunkPriority(chunk);
            }
        }
    }

    private void updateChunkPriority(Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        long chunkKey = chunkManager.getChunkKey(chunk);
        WorldConfig config = getWorldConfig(worldName);

        ChunkData chunkData = chunkDataMap.getOrDefault(worldName, Collections.emptyMap()).get(chunkKey);
        if (chunkData == null) return;

        boolean isPlayerChunk = playerChunks.getOrDefault(worldName, Collections.emptySet()).contains(chunkKey);
        int entityCount = countEntities(chunk);

        chunkData.isPlayerChunk = isPlayerChunk;
        chunkData.entityCount = entityCount;

        if (isPlayerChunk) {
            chunkData.unloadDelay = config.playerUnloadDelay;
            chunkData.priority = ChunkPriority.HIGH;
        } else if (entityCount > config.highEntityThreshold) {
            chunkData.unloadDelay = config.highEntityUnloadDelay;
            chunkData.priority = ChunkPriority.MEDIUM;
        } else if (entityCount < config.lowEntityThreshold) {
            chunkData.unloadDelay = config.lowEntityUnloadDelay;
            chunkData.priority = ChunkPriority.LOW;
        } else {
            chunkData.unloadDelay = calculateDynamicDelay(config.baseUnloadDelay);
            chunkData.priority = ChunkPriority.NORMAL;
        }
    }

    private int countEntities(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Player)) {
                count++;
            }
        }
        return count;
    }

    private int calculateDynamicDelay(int baseDelay) {
        double playerFactor = Math.max(0.5, 1.0 - (onlinePlayers * 0.05));
        return (int) (baseDelay * playerFactor);
    }

    @Override
    public void tick() {
        updatePlayerChunks();
        checkChunkUnloads();
        balanceLoadedChunks();
    }

    private void updatePlayerChunks() {
        playerChunks.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (world == null) continue;

            String worldName = world.getName();
            WorldConfig config = getWorldConfig(worldName);
            int priorityRadius = config.priorityRadius;

            Location loc = player.getLocation();
            int centerX = loc.getBlockX() >> 4;
            int centerZ = loc.getBlockZ() >> 4;

            Set<Long> worldPlayerChunks = playerChunks.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());

            for (int x = centerX - priorityRadius; x <= centerX + priorityRadius; x++) {
                for (int z = centerZ - priorityRadius; z <= centerZ + priorityRadius; z++) {
                    Chunk chunk = world.getChunkAt(x, z);
                    long chunkKey = chunkManager.getChunkKey(chunk);
                    worldPlayerChunks.add(chunkKey);
                }
            }
        }

        updateAllChunkPriorities();
    }

    private void checkChunkUnloads() {
        long currentTime = System.currentTimeMillis();

        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            Map<Long, Long> chunkLoadTimes = chunkManager.getChunkLoadTimes().getOrDefault(worldName, null);
            Map<Long, ChunkData> chunkData = chunkDataMap.getOrDefault(worldName, null);

            if (chunkLoadTimes == null || chunkData == null) continue;

            for (Map.Entry<Long, ChunkData> entry : chunkData.entrySet()) {
                long chunkKey = entry.getKey();
                ChunkData chunkDataEntry = entry.getValue();
                Long loadTime = chunkLoadTimes.get(chunkKey);

                if (loadTime == null) continue;

                long unloadDelayMillis = chunkDataEntry.unloadDelay * 50;
                if (currentTime - loadTime > unloadDelayMillis) {
                    int x = (int) (chunkKey >> 32);
                    int z = (int) (chunkKey & 0xFFFFFFFFL);

                    Chunk chunk = world.getChunkAt(x, z);
                    if (chunk.isLoaded() && !isChunkInUse(chunk)) {
                        chunk.unload(true);

                        if (chunkManager.isDebugMode()) {
                            chunkManager.logDebug("Chunk unloaded by strategy D: " + x + "," + z +
                                    " in world " + worldName);
                        }
                    }
                }
            }
        }
    }

    private void balanceLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            WorldConfig config = getWorldConfig(worldName);
            int maxLoaded = config.maxLoadedChunks;
            Chunk[] loadedChunks = world.getLoadedChunks();

            if (loadedChunks.length <= maxLoaded) continue;

            int chunksToUnload = loadedChunks.length - maxLoaded;
            List<Chunk> chunksToConsider = new ArrayList<>(Arrays.asList(loadedChunks));

            chunksToConsider.sort((a, b) -> {
                long keyA = chunkManager.getChunkKey(a);
                long keyB = chunkManager.getChunkKey(b);

                ChunkData dataA = chunkDataMap.getOrDefault(worldName, Collections.emptyMap()).get(keyA);
                ChunkData dataB = chunkDataMap.getOrDefault(worldName, Collections.emptyMap()).get(keyB);

                ChunkPriority priorityA = (dataA != null) ? dataA.priority : ChunkPriority.LOW;
                ChunkPriority priorityB = (dataB != null) ? dataB.priority : ChunkPriority.LOW;

                if (priorityA != priorityB) {
                    return priorityA.compareTo(priorityB);
                }

                Long timeA = chunkManager.getChunkLoadTimes().getOrDefault(worldName, Collections.emptyMap()).get(keyA);
                Long timeB = chunkManager.getChunkLoadTimes().getOrDefault(worldName, Collections.emptyMap()).get(keyB);

                if (timeA == null && timeB == null) return 0;
                if (timeA == null) return 1;
                if (timeB == null) return -1;

                return timeA.compareTo(timeB);
            });

            for (int i = 0; i < chunksToUnload && i < chunksToConsider.size(); i++) {
                Chunk chunk = chunksToConsider.get(i);
                long chunkKey = chunkManager.getChunkKey(chunk);
                Set<Long> worldPlayerChunks = playerChunks.getOrDefault(worldName, Collections.emptySet());

                if (!isChunkInUse(chunk) && !worldPlayerChunks.contains(chunkKey)) {
                    chunk.unload(true);

                    if (chunkManager.isDebugMode()) {
                        chunkManager.logDebug("Chunk unloaded for balance: " + chunk.getX() + "," + chunk.getZ() +
                                " in world " + worldName);
                    }
                }
            }
        }
    }

    private boolean isChunkInUse(Chunk chunk) {
        if (chunkManager.getVersion() >= 13) {
            try {
                return (boolean) Chunk.class.getMethod("isForceLoaded").invoke(chunk);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void onDisable() {
        worldConfigs.clear();
        chunkDataMap.clear();
        playerChunks.clear();

        if (chunkManager.isDebugMode()) {
            chunkManager.logDebug("ChunkLoadedD disabled");
        }
    }

    private static class WorldConfig {
        int maxLoadedChunks = 500;
        int baseUnloadDelay = 600;
        int playerUnloadDelay = 1200;
        int highEntityThreshold = 50;
        int highEntityUnloadDelay = 1800;
        int lowEntityThreshold = 10;
        int lowEntityUnloadDelay = 300;
        int priorityRadius = 3;
    }

    private static class ChunkData {
        boolean isPlayerChunk = false;
        int entityCount = 0;
        int unloadDelay = 600;
        ChunkPriority priority = ChunkPriority.NORMAL;
    }

    private enum ChunkPriority {
        LOW, NORMAL, MEDIUM, HIGH
    }
}
