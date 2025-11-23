package eternalpolar.spigot.eternaloptimization.Module.Other;

import eternalpolar.spigot.eternaloptimization.EternalOptimization;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketsReceiveOptimizated implements Listener {

    private final EternalOptimization plugin;
    private final ProtocolManager protocolManager;
    private final Map<Player, PacketHandler> playerHandlers = new ConcurrentHashMap<>();
    private final Map<String, Integer> packetCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> blockedPacketCounts = new ConcurrentHashMap<>();

    private boolean enabled;
    private int entityMoveThreshold;
    private int blockChangeThreshold;
    private int chatMessageThreshold;
    private int animationThreshold;
    private int armSwingThreshold;
    private int heldItemChangeThreshold;
    private int playerActionThreshold;
    private int packetLimit;
    private long packetResetInterval;

    private FileConfiguration packetConfig;

    public PacketsReceiveOptimizated(EternalOptimization plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin.getLogger().warning("ProtocolLib not found! Packet optimization will be disabled.");
            enabled = false;
            return;
        }

        loadConfig();
        if (enabled) {
            try {
                Bukkit.getPluginManager().registerEvents(this, plugin);
                registerPacketListeners();
                startPacketResetTask();
                plugin.getLogger().info("Packet optimization enabled successfully!");
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize packet optimization: " + e.getMessage());
                e.printStackTrace();
                enabled = false;
            }
        } else {
            plugin.getLogger().info("Packet optimization is disabled in configuration.");
        }
    }

    public void loadConfig() {
        try {
            File configFile = new File(plugin.getDataFolder(), "functions/packetOptimization.yml");

            if (!configFile.exists()) {
                plugin.saveResource("functions/packetOptimization.yml", false);
            }

            packetConfig = YamlConfiguration.loadConfiguration(configFile);

            enabled = packetConfig.getBoolean("enabled", true);
            entityMoveThreshold = packetConfig.getInt("entity-move-threshold", 5);
            blockChangeThreshold = packetConfig.getInt("block-change-threshold", 10);
            chatMessageThreshold = packetConfig.getInt("chat-message-threshold", 3);
            animationThreshold = packetConfig.getInt("animation-threshold", 8);
            armSwingThreshold = packetConfig.getInt("arm-swing-threshold", 6);
            heldItemChangeThreshold = packetConfig.getInt("held-item-change-threshold", 4);
            playerActionThreshold = packetConfig.getInt("player-action-threshold", 7);
            packetLimit = packetConfig.getInt("packet-limit", 100);
            packetResetInterval = packetConfig.getLong("packet-reset-interval", 5);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load packet optimization configuration: " + e.getMessage());
            e.printStackTrace();
            enabled = false;
        }
    }

    private void registerPacketListeners() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK,
                PacketType.Play.Client.LOOK,
                PacketType.Play.Client.BLOCK_DIG,
                PacketType.Play.Client.BLOCK_PLACE,
                PacketType.Play.Client.CHAT,
                PacketType.Play.Client.ARM_ANIMATION,
                PacketType.Play.Client.USE_ENTITY,
                PacketType.Play.Client.HELD_ITEM_SLOT,
                PacketType.Play.Client.CLIENT_COMMAND,
                PacketType.Play.Client.VEHICLE_MOVE,
                PacketType.Play.Client.STEER_VEHICLE) {

            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (!enabled || event.isCancelled()) {
                    return;
                }

                Player player = event.getPlayer();
                String packetType = event.getPacketType().name();

                if (shouldOptimizePacket(packetType)) {
                    PacketHandler handler = playerHandlers.get(player);
                    if (handler != null) {
                        if (handler.isPacketRateLimited(packetType)) {
                            event.setCancelled(true);
                            incrementBlockedPacketCount(packetType);
                            return;
                        }
                    }
                }

                incrementPacketCount(packetType);
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        PacketHandler handler = new PacketHandler(player);
        playerHandlers.put(player, handler);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        playerHandlers.remove(player);
    }

    private void startPacketResetTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (PacketHandler handler : playerHandlers.values()) {
                handler.resetCounters();
            }
            packetCounts.clear();
            blockedPacketCounts.clear();
        }, 0, packetResetInterval * 20);
    }

    public void disable() {
        if (!enabled) return;

        protocolManager.removePacketListeners(plugin);
        playerHandlers.clear();
        enabled = false;
    }

    private boolean shouldOptimizePacket(String packetType) {
        return packetType.contains("POSITION") ||
                packetType.contains("LOOK") ||
                packetType.contains("BLOCK_DIG") ||
                packetType.contains("BLOCK_PLACE") ||
                packetType.contains("CHAT") ||
                packetType.contains("ANIMATION") ||
                packetType.contains("USE_ENTITY") ||
                packetType.contains("HELD_ITEM") ||
                packetType.contains("CLIENT_COMMAND") ||
                packetType.contains("VEHICLE_MOVE") ||
                packetType.contains("STEER_VEHICLE");
    }

    private void incrementPacketCount(String packetType) {
        packetCounts.put(packetType, packetCounts.getOrDefault(packetType, 0) + 1);
    }

    private void incrementBlockedPacketCount(String packetType) {
        blockedPacketCounts.put(packetType, blockedPacketCounts.getOrDefault(packetType, 0) + 1);
    }

    private class PacketHandler {

        private final Player player;
        private final Map<String, Integer> packetCounters = new HashMap<>();
        private long lastEntityMoveTime = 0;
        private long lastBlockChangeTime = 0;
        private long lastChatMessageTime = 0;
        private long lastAnimationTime = 0;
        private long lastArmSwingTime = 0;
        private long lastHeldItemChangeTime = 0;
        private long lastPlayerActionTime = 0;

        public PacketHandler(Player player) {
            this.player = player;
        }

        public boolean isPacketRateLimited(String packetType) {
            long currentTime = System.currentTimeMillis();

            if (packetType.contains("POSITION") || packetType.contains("LOOK")) {
                if (currentTime - lastEntityMoveTime < 50) {
                    int count = packetCounters.getOrDefault("move", 0) + 1;
                    packetCounters.put("move", count);
                    if (count > entityMoveThreshold) {
                        return true;
                    }
                } else {
                    packetCounters.put("move", 1);
                    lastEntityMoveTime = currentTime;
                }
            }
            else if (packetType.contains("BLOCK_DIG") || packetType.contains("BLOCK_PLACE")) {
                if (currentTime - lastBlockChangeTime < 50) {
                    int count = packetCounters.getOrDefault("block", 0) + 1;
                    packetCounters.put("block", count);
                    if (count > blockChangeThreshold) {
                        return true;
                    }
                } else {
                    packetCounters.put("block", 1);
                    lastBlockChangeTime = currentTime;
                }
            }
            else if (packetType.contains("CHAT")) {
                if (currentTime - lastChatMessageTime < 1000) {
                    int count = packetCounters.getOrDefault("chat", 0) + 1;
                    packetCounters.put("chat", count);
                    if (count > chatMessageThreshold) {
                        return true;
                    }
                } else {
                    packetCounters.put("chat", 1);
                    lastChatMessageTime = currentTime;
                }
            }
            else if (packetType.contains("ARM_ANIMATION")) {
                if (currentTime - lastAnimationTime < 50) {
                    int count = packetCounters.getOrDefault("animation", 0) + 1;
                    packetCounters.put("animation", count);
                    if (count > animationThreshold) {
                        return true;
                    }
                } else {
                    packetCounters.put("animation", 1);
                    lastAnimationTime = currentTime;
                }
            }
            else if (packetType.contains("USE_ENTITY")) {
                if (currentTime - lastArmSwingTime < 50) {
                    int count = packetCounters.getOrDefault("armswing", 0) + 1;
                    packetCounters.put("armswing", count);
                    if (count > armSwingThreshold) {
                        return true;
                    }
                } else {
                    packetCounters.put("armswing", 1);
                    lastArmSwingTime = currentTime;
                }
            }
            else if (packetType.contains("HELD_ITEM")) {
                if (currentTime - lastHeldItemChangeTime < 100) {
                    int count = packetCounters.getOrDefault("helditem", 0) + 1;
                    packetCounters.put("helditem", count);
                    if (count > heldItemChangeThreshold) {
                        return true;
                    }
                } else {
                    packetCounters.put("helditem", 1);
                    lastHeldItemChangeTime = currentTime;
                }
            }
            else if (packetType.contains("CLIENT_COMMAND")) {
                if (currentTime - lastPlayerActionTime < 50) {
                    int count = packetCounters.getOrDefault("playeraction", 0) + 1;
                    packetCounters.put("playeraction", count);
                    if (count > playerActionThreshold) {
                        return true;
                    }
                } else {
                    packetCounters.put("playeraction", 1);
                    lastPlayerActionTime = currentTime;
                }
            }

            return false;
        }

        public void resetCounters() {
            packetCounters.clear();
            lastEntityMoveTime = 0;
            lastBlockChangeTime = 0;
            lastChatMessageTime = 0;
            lastAnimationTime = 0;
            lastArmSwingTime = 0;
            lastHeldItemChangeTime = 0;
            lastPlayerActionTime = 0;
        }
    }

    public Map<String, Integer> getPacketCounts() {
        return new HashMap<>(packetCounts);
    }

    public Map<String, Integer> getBlockedPacketCounts() {
        return new HashMap<>(blockedPacketCounts);
    }

    public int getTotalProcessedPackets() {
        int total = 0;
        for (int count : packetCounts.values()) {
            total += count;
        }
        return total;
    }

    public int getTotalBlockedPackets() {
        int total = 0;
        for (int count : blockedPacketCounts.values()) {
            total += count;
        }
        return total;
    }

    public double getPacketOptimizationRate() {
        int processed = getTotalProcessedPackets();
        int blocked = getTotalBlockedPackets();
        int total = processed + blocked;

        if (total == 0) {
            return 0.0;
        }

        return ((double) blocked / total) * 100;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reloadConfig() {
        loadConfig();
    }
}