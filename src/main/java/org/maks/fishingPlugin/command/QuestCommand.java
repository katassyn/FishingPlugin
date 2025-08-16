package org.maks.fishingPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.gui.QuestMenu;

/**
 * Command to open the quest menu.
 */
public class QuestCommand implements CommandExecutor {

  private final QuestMenu menu;

  public QuestCommand(QuestMenu menu) {
    this.menu = menu;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use this command.");
      return true;
    }
    if (!player.hasPermission("fishing.use")) {
      player.sendMessage("You don't have permission.");
      return true;
    }
    menu.open(player);
    return true;
  }
}
