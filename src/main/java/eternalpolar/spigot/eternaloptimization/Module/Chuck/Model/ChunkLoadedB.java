package eternalpolar.spigot.eternaloptimization.Module.Chuck.Model;

import eternalpolar.spigot.eternaloptimization.Module.Chuck.ChunkManager;
import eternalpolar.spigot.eternaloptimization.Module.Chuck.ChunkOptimizationStrategy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChunkLoadedB implements ChunkOptimizationStrategy {

    private final ChunkManager chunkManager;
    private final Queue<ChunkLoadRequest> loadQueue = new ConcurrentLinkedQueue<>();
    private final int loadThreshold;
    private final int chunksPerTick;
    private int currentTickLoads;
    private Method setCancelledMethod;

    public ChunkLoadedB(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
        FileConfiguration config = chunkManager.getConfig();
        this.loadThreshold = config.getInt("optimization-strategies.strategyB.load-threshold", 300);
        this.chunksPerTick = config.getInt("optimization-strategies.strategyB.chunks-per-tick", 350);
        this.currentTickLoads = 0;

        try {
            this.setCancelledMethod = ChunkLoadEvent.class.getMethod("setCancelled", boolean.class);
        } catch (NoSuchMethodException e) {
            this.setCancelledMethod = null;
        }

        startLoadTask();

        if (chunkManager.isDebugMode()) {
            chunkManager.logDebug("ChunkLoadedB initialized - Threshold: " + loadThreshold + ", Per tick: " + chunksPerTick +
                    ", Can cancel chunk load: " + (setCancelledMethod != null));
        }
    }

    private void startLoadTask() {
        Bukkit.getScheduler().runTaskTimer(chunkManager.getPlugin(), () -> {
            currentTickLoads = 0;
            processLoadQueue();
        }, 1, 1);
    }

    @Override
    public void onChunkLoad(ChunkLoadEvent event) {
        if (isOverThreshold() && setCancelledMethod != null) {
            try {
                setCancelledMethod.invoke(event, true);
                Chunk chunk = event.getChunk();
                loadQueue.add(new ChunkLoadRequest(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), System.currentTimeMillis()));
            } catch (Exception e) {
                if (chunkManager.isDebugMode()) {
                    chunkManager.logDebug("Failed to cancel chunk load event");
                }
            }
        }
    }

    @Override
    public void onChunkUnload(ChunkUnloadEvent event) {

    }

    @Override
    public void tick() {

    }

    private void processLoadQueue() {
        while (currentTickLoads < chunksPerTick && !loadQueue.isEmpty()) {
            ChunkLoadRequest request = loadQueue.poll();
            if (request == null) break;

            World world = Bukkit.getWorld(request.worldName);
            if (world == null) continue;

            if (!isOverThreshold()) {
                Chunk chunk = world.getChunkAt(request.x, request.z);
                if (!chunk.isLoaded()) {
                    chunk.load(false);
                    currentTickLoads++;
                }
            } else {
                loadQueue.add(request);
                break;
            }
        }
    }

    private boolean isOverThreshold() {
        int totalLoaded = 0;
        for (World world : Bukkit.getWorlds()) {
            totalLoaded += world.getLoadedChunks().length;
        }
        return totalLoaded > loadThreshold;
    }

    @Override
    public void onDisable() {
        loadQueue.clear();
    }

    private static class ChunkLoadRequest {
        String worldName;
        int x;
        int z;
        long timestamp;

        ChunkLoadRequest(String worldName, int x, int z, long timestamp) {
            this.worldName = worldName;
            this.x = x;
            this.z = z;
            this.timestamp = timestamp;
        }
    }
}
