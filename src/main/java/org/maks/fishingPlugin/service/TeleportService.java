package org.maks.fishingPlugin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.fishingPlugin.model.Warp;
import org.maks.fishingPlugin.model.WarpType;

public class TeleportService {

  private final Map<String, Warp> warps = new HashMap<>();

  public TeleportService(JavaPlugin plugin) {
    ConfigurationSection sec = plugin.getConfig();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list = (List<Map<String, Object>>) (List<?>) sec.getMapList("warps");
    for (Map<String, Object> m : list) {
      String key = (String) m.get("key");
      String typeStr = (String) m.get("type");
      WarpType type = WarpType.valueOf(typeStr.toUpperCase());
      String command = (String) m.getOrDefault("command", "");
      String world = (String) m.getOrDefault("world", "world");
      double x = ((Number) m.getOrDefault("x", 0)).doubleValue();
      double y = ((Number) m.getOrDefault("y", 0)).doubleValue();
      double z = ((Number) m.getOrDefault("z", 0)).doubleValue();
      warps.put(key, new Warp(key, type, command, world, x, y, z));
    }
  }

  public boolean teleport(String key, org.bukkit.entity.Player player) {
    Warp warp = warps.get(key);
    if (warp != null) {
      warp.teleport(player);
      return true;
    }
    // Fallback: attempt to run a generic warp command if no explicit warp is configured
    String cmd = "warp " + key + " " + player.getName();
    return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
  }
}
