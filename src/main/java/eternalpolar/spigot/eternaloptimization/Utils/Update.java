package eternalpolar.spigot.eternaloptimization.Utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class Update implements Listener {

    private final JavaPlugin plugin;
    private final String resourceUrl;

    public Update(JavaPlugin plugin) {
        this.plugin = plugin;
        this.resourceUrl = "https://api.spigotmc.org/legacy/update.php?resource=130288";
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URLConnection connection = new URL(resourceUrl).openConnection();
                connection.setConnectTimeout(5000);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {

                    String latestVersion = reader.readLine();
                    if (latestVersion != null &&
                            !latestVersion.equals(plugin.getDescription().getVersion())) {

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e✧ &bEternal&3Optimization &f has an update &7(&b " + latestVersion + " &7) &f, please check update in SpigotMC!&e ✧"));
                        });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e✧ &bEternal&3Optimization &fis the lastest version! ✧"));
                        });
                    }
                }
            } catch (Exception e) {
            }
        });
    }
}