package org.maks.fishingPlugin.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public record Warp(String key, WarpType type, String command, String world, double x, double y, double z) {

  public void teleport(Player player) {
    if (type == WarpType.COMMAND) {
      String cmd = command.replace("%player%", player.getName());
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    } else if (type == WarpType.COORDS) {
      Location loc = new Location(Bukkit.getWorld(world), x, y, z);
      player.teleport(loc);
    }
  }
}
