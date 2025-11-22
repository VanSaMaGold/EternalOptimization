package eternalpolar.spigot.eternaloptimization.Module.Chuck.Model;

import eternalpolar.spigot.eternaloptimization.Module.Chuck.ChunkManager;
import eternalpolar.spigot.eternaloptimization.Module.Chuck.ChunkOptimizationStrategy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChunkLoadedC implements ChunkOptimizationStrategy, Listener {

    private final ChunkManager chunkManager;
    private final boolean asyncLoading;
    private final int preloadRadius;
    private final ExecutorService asyncExecutor;
    private final Map<String, Set<Long>> preloadedChunks = new ConcurrentHashMap<>();

    public ChunkLoadedC(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
        FileConfiguration config = chunkManager.getConfig();
        this.asyncLoading = config.getBoolean("optimization-strategies.strategyC.async-loading", true);
        this.preloadRadius = config.getInt("optimization-strategies.strategyC.preload-radius", 2);
        this.asyncExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        Bukkit.getPluginManager().registerEvents(this, chunkManager.getPlugin());

        if (chunkManager.isDebugMode()) {
            chunkManager.logDebug("ChunkLoadedC initialized - Async: " + asyncLoading + ", Radius: " + preloadRadius);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        if (to.getBlockX() >> 4 != from.getBlockX() >> 4 || to.getBlockZ() >> 4 != from.getBlockZ() >> 4) {
            preloadChunksAround(player, to);

            if (chunkManager.isDebugMode()) {
                chunkManager.logDebug("Player moved, preloading chunks around " + to.getBlockX() + "," + to.getBlockZ());
            }
        }
    }

    private void preloadChunksAround(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) return;

        String worldName = world.getName();
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;

        for (int x = centerX - preloadRadius; x <= centerX + preloadRadius; x++) {
            for (int z = centerZ - preloadRadius; z <= centerZ + preloadRadius; z++) {
                long chunkKey = chunkManager.getChunkKey(world.getChunkAt(x, z));

                if (!preloadedChunks.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet()).contains(chunkKey)) {
                    loadChunk(world, x, z);
                    preloadedChunks.get(worldName).add(chunkKey);
                }
            }
        }
    }

    private void loadChunk(World world, int x, int z) {
        if (asyncLoading) {
            asyncExecutor.submit(() -> loadChunkSync(world, x, z));
        } else {
            loadChunkSync(world, x, z);
        }
    }

    private void loadChunkSync(World world, int x, int z) {
        if (!world.isChunkLoaded(x, z)) {
            world.getChunkAt(x, z);

            if (chunkManager.isDebugMode()) {
                chunkManager.logDebug("Chunk loaded: " + x + "," + z + " in world " + world.getName());
            }
        }
    }

    @Override
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        long chunkKey = chunkManager.getChunkKey(chunk);

        preloadedChunks.getOrDefault(worldName, Collections.emptySet()).remove(chunkKey);

        chunkManager.markChunkAsOptimized(chunk);
    }

    @Override
    public void onChunkUnload(ChunkUnloadEvent event) {

    }

    @Override
    public void tick() {
        for (Map.Entry<String, Set<Long>> entry : preloadedChunks.entrySet()) {
            String worldName = entry.getKey();
            World world = Bukkit.getWorld(worldName);

            if (world == null) continue;

            Iterator<Long> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                long chunkKey = iterator.next();
                int x = (int) (chunkKey >> 32);
                int z = (int) (chunkKey & 0xFFFFFFFFL);

                if (!world.isChunkLoaded(x, z)) {
                    iterator.remove();
                }
            }
        }
    }

    @Override
    public void onDisable() {
        asyncExecutor.shutdown();
        preloadedChunks.clear();

        if (chunkManager.isDebugMode()) {
            chunkManager.logDebug("ChunkLoadedC disabled");
        }
    }
}
