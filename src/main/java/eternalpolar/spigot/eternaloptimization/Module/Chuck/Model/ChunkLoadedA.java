package eternalpolar.spigot.eternaloptimization.Module.Chuck.Model;

import eternalpolar.spigot.eternaloptimization.Module.Chuck.ChunkManager;
import eternalpolar.spigot.eternaloptimization.Module.Chuck.ChunkOptimizationStrategy;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkLoadedA implements ChunkOptimizationStrategy {

    private final ChunkManager chunkManager;
    private final Map<String, Integer> loadedChunkCounts = new ConcurrentHashMap<>();
    private final int maxLoadedChunks;
    private final int unloadDelayTicks;

    public ChunkLoadedA(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
        FileConfiguration config = chunkManager.getConfig();
        this.maxLoadedChunks = config.getInt("optimization-strategies.strategyA.max-loaded-chunks", 500);
        this.unloadDelayTicks = config.getInt("optimization-strategies.strategyA.unload-delay-ticks", 600);

        if (chunkManager.isDebugMode()) {
            chunkManager.logDebug("ChunkLoadedA initialized - Max chunks: " + maxLoadedChunks + ", Unload delay: " + unloadDelayTicks);
        }
    }

    @Override
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();

        loadedChunkCounts.put(worldName, loadedChunkCounts.getOrDefault(worldName, 0) + 1);

        if (loadedChunkCounts.get(worldName) > maxLoadedChunks) {
            unloadOldestChunks(chunk.getWorld());
        }
    }

    @Override
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();

        loadedChunkCounts.put(worldName, Math.max(0, loadedChunkCounts.getOrDefault(worldName, 0) - 1));
    }

    @Override
    public void tick() {
        long currentTime = System.currentTimeMillis();
        long delayMillis = unloadDelayTicks * 50;

        for (World world : chunkManager.getPlugin().getServer().getWorlds()) {
            String worldName = world.getName();
            Map<Long, Long> chunkLoadTimes = chunkManager.getChunkLoadTimes().getOrDefault(worldName, null);

            if (chunkLoadTimes == null) continue;

            for (Map.Entry<Long, Long> entry : chunkLoadTimes.entrySet()) {
                if (currentTime - entry.getValue() > delayMillis) {
                    int x = (int) (entry.getKey() >> 32);
                    int z = (int) (entry.getKey() & 0xFFFFFFFFL);

                    Chunk chunk = world.getChunkAt(x, z);
                    if (chunk.isLoaded() && !isChunkInUse(chunk)) {
                        chunk.unload(true);
                    }
                }
            }
        }
    }

    private void unloadOldestChunks(World world) {
        String worldName = world.getName();
        Map<Long, Long> chunkLoadTimes = chunkManager.getChunkLoadTimes().getOrDefault(worldName, null);

        if (chunkLoadTimes == null) return;

        int currentCount = loadedChunkCounts.getOrDefault(worldName, 0);
        int chunksToUnload = currentCount - maxLoadedChunks;

        if (chunksToUnload <= 0) return;

        chunkLoadTimes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(chunksToUnload)
                .forEach(entry -> {
                    int x = (int) (entry.getKey() >> 32);
                    int z = (int) (entry.getKey() & 0xFFFFFFFFL);

                    Chunk chunk = world.getChunkAt(x, z);
                    if (chunk.isLoaded() && !isChunkInUse(chunk)) {
                        chunk.unload(true);
                    }
                });
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
        loadedChunkCounts.clear();
    }
}
