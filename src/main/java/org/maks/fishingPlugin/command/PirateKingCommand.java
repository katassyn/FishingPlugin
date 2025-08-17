package org.maks.fishingPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.gui.PirateKingMenu;

/**
 * Command handler for the Pirate King bounty system.
 */
public class PirateKingCommand implements CommandExecutor {

  private final PirateKingMenu menu;

  public PirateKingCommand(PirateKingMenu menu) {
    this.menu = menu;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players may use this command.");
      return true;
    }
    if (!player.hasPermission("fishing.pirateking")) {
      player.sendMessage("You don't have permission.");
      return true;
    }
    menu.open(player);
    return true;
  }
}
