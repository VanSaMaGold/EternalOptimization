package eternalpolar.spigot.eternaloptimization.Module.Chuck;

import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public interface ChunkOptimizationStrategy {

    void onChunkLoad(ChunkLoadEvent event);

    void onChunkUnload(ChunkUnloadEvent event);

    void tick();

    void onDisable();
}
