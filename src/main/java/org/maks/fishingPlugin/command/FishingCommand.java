package org.maks.fishingPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.gui.MainMenu;

/**
 * Command that opens the main fishing menu.
 */
public class FishingCommand implements CommandExecutor {

  private final MainMenu mainMenu;
  private final int requiredLevel;

  public FishingCommand(MainMenu mainMenu, int requiredLevel) {
    this.mainMenu = mainMenu;
    this.requiredLevel = requiredLevel;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use this command.");
      return true;
    }
    if (player.getLevel() < requiredLevel) {
      player.sendMessage("You need level " + requiredLevel + " to use fishing features.");
      return true;
    }
    mainMenu.open(player);
    return true;
  }
}
