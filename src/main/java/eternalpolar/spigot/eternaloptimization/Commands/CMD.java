package eternalpolar.spigot.eternaloptimization.Commands;

import eternalpolar.spigot.eternaloptimization.EternalOptimization;
import eternalpolar.spigot.eternaloptimization.Module.Load.ChuckLoadOptimizated;
import eternalpolar.spigot.eternaloptimization.Module.Load.ChuckPreloadOptimizated;
import eternalpolar.spigot.eternaloptimization.Module.Other.PacketsReceiveOptimizated;
import eternalpolar.spigot.eternaloptimization.Utils.PerformanceMonitor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class CMD implements CommandExecutor {
    private final EternalOptimization plugin;
    private final ChuckLoadOptimizated chuckOptimizer;
    private final ChuckPreloadOptimizated chuckPreloadOptimizer;
    private final PerformanceMonitor performanceMonitor;
    private final PacketsReceiveOptimizated packetOptimizer;
    private final boolean hasProtocolLib;

    private final String noPermissionMsg = ChatColor.RED + "You don't have permission to use this command";
    private final String reloadSuccessMsg = ChatColor.GREEN + "Configuration reloaded successfully";
    private final String unknownSubcommandMsg = ChatColor.RED + "Unknown sub-command. Type /eternaloptimization for help.";
    private final String memoryStatsMsg = ChatColor.GOLD + "Memory Stats: %.2f/%.2f MB (%.1f%%)";

    public CMD(EternalOptimization plugin) {
        this.plugin = plugin;
        this.chuckOptimizer = plugin.getChuckOptimizer();
        this.chuckPreloadOptimizer = plugin.getChuckPreloadOptimizer();
        this.performanceMonitor = plugin.getPerformanceMonitor();
        this.packetOptimizer = plugin.getPacketOptimizer();
        this.hasProtocolLib = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("eternaloptimization.admin")) {
                    sender.sendMessage(noPermissionMsg);
                    return true;
                }
                return handleReload(sender);
            case "status":
                return handleStatus(sender);
            case "packets":
                return handlePackets(sender);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage(unknownSubcommandMsg);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();

        if (chuckOptimizer != null) {
            try {
                java.lang.reflect.Method loadConfigMethod = ChuckLoadOptimizated.class.getDeclaredMethod("loadConfig");
                loadConfigMethod.setAccessible(true);
                loadConfigMethod.invoke(chuckOptimizer);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to reload chunk unload configuration: " + e.getMessage());
                return true;
            }
        }

        if (chuckPreloadOptimizer != null) {
            try {
                chuckPreloadOptimizer.reloadConfig();
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to reload chunk preload configuration: " + e.getMessage());
                return true;
            }
        }

        if (hasProtocolLib && packetOptimizer != null) {
            try {
                packetOptimizer.reloadConfig();
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to reload packet configuration: " + e.getMessage());
                return true;
            }
        }

        sender.sendMessage(reloadSuccessMsg);
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("eternaloptimization.admin")) {
            sender.sendMessage(noPermissionMsg);
            return true;
        }

        long usedMemory = performanceMonitor.getUsedMemory();
        long maxMemory = performanceMonitor.getMaxMemory();
        double usagePercent = performanceMonitor.getMemoryUsagePercent();
        double memoryImprovement = performanceMonitor.getMemoryImprovement();

        sender.sendMessage(ChatColor.GOLD + "=== EternalOptimization Status ===");
        sender.sendMessage(String.format(memoryStatsMsg,
                usedMemory / (1024.0 * 1024.0),
                maxMemory / (1024.0 * 1024.0),
                usagePercent));
        sender.sendMessage(String.format(ChatColor.GOLD + "Memory Improvement: %.2f%%", memoryImprovement));
        sender.sendMessage("");

        sender.sendMessage(ChatColor.GOLD + "Chunk Status:");
        int totalLoaded = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            int loaded = world.getLoadedChunks().length;
            totalLoaded += loaded;
            int preloaded = chuckPreloadOptimizer != null ? chuckPreloadOptimizer.getPreloadedChunkCount(world.getName()) : 0;
            sender.sendMessage(ChatColor.GRAY + world.getName() + ": " + loaded + " loaded chunks | " + preloaded + " preloaded chunks");
        }
        sender.sendMessage(ChatColor.GRAY + "Total loaded chunks: " + totalLoaded);
        if (chuckPreloadOptimizer != null) {
            sender.sendMessage(ChatColor.GRAY + "Total preloaded chunks: " + chuckPreloadOptimizer.getTotalPreloadedChunks());
        }
        sender.sendMessage("");

        sender.sendMessage(ChatColor.GOLD + "Optimization Status:");
        sender.sendMessage(ChatColor.GRAY + "Chunk Load Optimization: " + (chuckOptimizer != null && chuckOptimizer.isEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        sender.sendMessage(ChatColor.GRAY + "Chunk Preload Optimization: " + (chuckPreloadOptimizer != null && chuckPreloadOptimizer.isEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        if (hasProtocolLib) {
            sender.sendMessage(ChatColor.GRAY + "Packet Optimization: " + (packetOptimizer != null && packetOptimizer.isEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        } else {
            sender.sendMessage(ChatColor.GRAY + "Packet Optimization: " + ChatColor.YELLOW + "ProtocolLib not installed");
        }

        sender.sendMessage(ChatColor.GOLD + "==================================");

        return true;
    }

    private boolean handlePackets(CommandSender sender) {
        if (!sender.hasPermission("eternaloptimization.admin")) {
            sender.sendMessage(noPermissionMsg);
            return true;
        }

        if (!hasProtocolLib) {
            sender.sendMessage(ChatColor.RED + "ProtocolLib is not installed. Packet optimization is unavailable.");
            return true;
        }

        if (packetOptimizer == null || !packetOptimizer.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Packet optimization is not enabled.");
            return true;
        }

        Map<String, Integer> packetCounts = packetOptimizer.getPacketCounts();
        Map<String, Integer> blockedPacketCounts = packetOptimizer.getBlockedPacketCounts();
        int totalProcessed = packetOptimizer.getTotalProcessedPackets();
        int totalBlocked = packetOptimizer.getTotalBlockedPackets();
        double optimizationRate = packetOptimizer.getPacketOptimizationRate();

        sender.sendMessage(ChatColor.GOLD + "=== Packet Statistics ===");
        sender.sendMessage(ChatColor.GRAY + "Last " + plugin.getConfig().getInt("packet-optimization.packet-reset-interval", 5) + " seconds:");
        sender.sendMessage("");

        if (packetCounts.isEmpty() && blockedPacketCounts.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No packet data available yet.");
        } else {
            sender.sendMessage(ChatColor.GRAY + "Processed Packets:");
            int processedTotal = 0;
            for (Map.Entry<String, Integer> entry : packetCounts.entrySet()) {
                String packetType = entry.getKey().replace("PacketPlayIn", "");
                int count = entry.getValue();
                processedTotal += count;
                sender.sendMessage(ChatColor.GRAY + "  " + packetType + ": " + ChatColor.WHITE + count);
            }
            sender.sendMessage(ChatColor.GRAY + "  Total Processed: " + ChatColor.WHITE + processedTotal);
            sender.sendMessage("");

            sender.sendMessage(ChatColor.GRAY + "Blocked Packets:");
            int blockedTotal = 0;
            for (Map.Entry<String, Integer> entry : blockedPacketCounts.entrySet()) {
                String packetType = entry.getKey().replace("PacketPlayIn", "");
                int count = entry.getValue();
                blockedTotal += count;
                sender.sendMessage(ChatColor.GRAY + "  " + packetType + ": " + ChatColor.WHITE + count);
            }
            sender.sendMessage(ChatColor.GRAY + "  Total Blocked: " + ChatColor.WHITE + blockedTotal);
            sender.sendMessage("");

            sender.sendMessage(ChatColor.AQUA + "Optimization Rate: " + ChatColor.WHITE + String.format("%.2f%%", optimizationRate));
        }

        sender.sendMessage(ChatColor.GOLD + "========================");

        return true;
    }

    private void sendHelp(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("eternaloptimization.admin");

        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "EternalOptimization " + ChatColor.GRAY + "v" + plugin.getDescription().getVersion());
        sender.sendMessage("");

        if (isAdmin) {
            sender.sendMessage(ChatColor.GRAY + "-> " + ChatColor.AQUA + "/eternaloptimization reload");
            sender.sendMessage(ChatColor.DARK_GRAY + "Reload all plugin configurations (chunk unload/preload + packet)");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "-> " + ChatColor.AQUA + "/eternaloptimization status");
            sender.sendMessage(ChatColor.DARK_GRAY + "Show plugin status, memory usage, chunk stats and optimization status");
            sender.sendMessage("");

            if (hasProtocolLib) {
                sender.sendMessage(ChatColor.GRAY + "-> " + ChatColor.AQUA + "/eternaloptimization packets");
                sender.sendMessage(ChatColor.DARK_GRAY + "Show packet optimization statistics");
                sender.sendMessage("");
            } else {
                sender.sendMessage(ChatColor.GRAY + "-> " + ChatColor.AQUA + "/eternaloptimization packets");
                sender.sendMessage(ChatColor.DARK_GRAY + "Show packet statistics (Requires ProtocolLib)");
                sender.sendMessage("");
            }
        }

        sender.sendMessage(ChatColor.GRAY + "-> " + ChatColor.AQUA + "/eternaloptimization help");
        sender.sendMessage(ChatColor.DARK_GRAY + "Show this help message");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Created by EternalPolar");
        sender.sendMessage("");
    }
}