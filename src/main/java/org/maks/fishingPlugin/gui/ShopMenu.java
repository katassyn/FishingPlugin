package org.maks.fishingPlugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Provides access to the external fisherman shop handled by another plugin.
 */
public class ShopMenu {

  private final JavaPlugin plugin;
  private final int requiredLevel;

  public ShopMenu(JavaPlugin plugin, int requiredLevel) {
    this.plugin = plugin;
    this.requiredLevel = requiredLevel;
  }

  /**
   * Grant temporary permission and execute the fisherman shop command.
   */
  public void open(Player player) {
    if (player.getLevel() < requiredLevel) {
      player.sendMessage("You need level " + requiredLevel + " to access the shop.");
      return;
    }
    PermissionAttachment attachment =
        player.addAttachment(plugin, "mycraftingplugin.use", true);
    player.performCommand("fisherman_shop");
    player.removeAttachment(attachment);
  }
}
