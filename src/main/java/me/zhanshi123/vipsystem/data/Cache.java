package me.zhanshi123.vipsystem.data;

import me.zhanshi123.vipsystem.Main;
import me.zhanshi123.vipsystem.api.VipSystemAPI;
import me.zhanshi123.vipsystem.api.vip.VipData;
import me.zhanshi123.vipsystem.task.CheckVipTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Cache implements Listener {
    private final Map<String, VipData> map = new ConcurrentHashMap<>();
    private final Map<String, Long> missAt = new ConcurrentHashMap<>();

    public Cache() {
        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());
    }


    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Player player = e.getPlayer();
                cache(player);
            }
        }.runTaskLaterAsynchronously(Main.getInstance(), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        deCache(player, true);
    }

    public void cache(Player player) {
        cache(player, 0);
    }

    private void cache(Player player, int retry) {
        if (!player.isOnline()) {
            Main.getInstance().debug("Player " + player.getName() + " is offline, skipping cache");
            return;
        }
        String name = VipSystemAPI.getInstance().getPlayerName(player);
        Long miss = missAt.get(name);
        if (miss != null && System.currentTimeMillis() - miss < 60000) {
            return;
        }
        Main.getInstance().debug("Attempting to download player " + player.getName() + "'s data from database");
        Optional<VipData> vipData;
        try {
            vipData = Main.getDataBase().tryGetVipData(name);
        } catch (SQLException e) {
            Main.getInstance().debug("Player " + player.getName() + " cache download failed");
            if (retry < 2) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        cache(player, retry + 1);
                    }
                }.runTaskLaterAsynchronously(Main.getInstance(), 40L);
            }
            return;
        }
        if (!vipData.isPresent()) {
            Main.getInstance().debug("Player " + player.getName() + "'s data not found");
            missAt.put(name, System.currentTimeMillis());
            return;
        }
        missAt.remove(name);
        Main.getInstance().debug("Cache player " + player.getName() + "'s data as " + name);
        Main.getInstance().debug("Data: " + vipData.get());
        map.remove(name);
        map.put(name, vipData.get());
        new BukkitRunnable() {
            @Override
            public void run() {
                new CheckVipTask(player).runTask(Main.getInstance());
            }
        }.runTask(Main.getInstance());

    }

    public VipData getVipData(String playerName) {
        return map.get(playerName);
    }

    public void removePlayer(String playerName) {
        map.remove(playerName);
    }

    public void deCache(Player player) {
        deCache(player, false);
    }

    public void deCache(Player player, boolean async) {
        String name = VipSystemAPI.getInstance().getPlayerName(player);
        Main.getInstance().debug("Attempting to deCache player " + player.getName() + "'s data as " + name);
        VipData vipData = getVipData(name);
        if (vipData == null) {
            Main.getInstance().debug("Player " + player.getName() + "'s cached data not found");
            return;
        }
        map.remove(name);
        if (async) {
            updateVipDataAsync(vipData);
        } else {
            Main.getDataBase().updateVipData(vipData);
        }
        Main.getInstance().debug("Player " + player.getName() + "'s cached data removed and updated in database");
        Main.getInstance().debug("New data: " + vipData);
    }

    private void updateVipDataAsync(VipData vipData) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Main.getDataBase().updateVipData(vipData);
            }
        }.runTaskAsynchronously(Main.getInstance());
    }

    public void addVipData(String playerName, VipData vipData) {
        map.remove(playerName);
        map.put(playerName, vipData);
    }

    public Map<String, VipData> getCachedData() {
        return new HashMap<>(map);
    }
}
