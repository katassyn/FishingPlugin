package org.maks.fishingPlugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Provides access to the external fisherman shop handled by another plugin.
 */
public class ShopMenu {

  private final JavaPlugin plugin;

  public ShopMenu(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Grant temporary permission and execute the fisherman shop command.
   */
  public void open(Player player) {
    PermissionAttachment attachment = player.addAttachment(plugin, "mycraftingplugin.use", true);
    player.performCommand("fisherman_shop");
    player.removeAttachment(attachment);
  }
}
