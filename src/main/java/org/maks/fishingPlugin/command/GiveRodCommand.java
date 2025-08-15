package org.maks.fishingPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.service.RodService;

/**
 * Command for administrators to receive the plugin's fishing rod.
 */
public class GiveRodCommand implements CommandExecutor {

  private final RodService rodService;

  public GiveRodCommand(RodService rodService) {
    this.rodService = rodService;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use this command.");
      return true;
    }
    if (!player.hasPermission("fishing.admin")) {
      player.sendMessage("You don't have permission.");
      return true;
    }
    rodService.giveRod(player);
    player.sendMessage("Fishing rod given.");
    return true;
  }
}
