package org.maks.fishingPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.gui.QuickSellMenu;

/**
 * Command to open the quick sell menu.
 */
public class QuickSellCommand implements CommandExecutor {

  private final QuickSellMenu menu;

  public QuickSellCommand(QuickSellMenu menu) {
    this.menu = menu;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use this command.");
      return true;
    }
    if (!player.hasPermission("fishing.sell")) {
      player.sendMessage("You don't have permission.");
      return true;
    }
    menu.open(player);
    return true;
  }
}
