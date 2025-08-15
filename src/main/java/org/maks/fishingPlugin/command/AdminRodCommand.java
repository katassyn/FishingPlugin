package org.maks.fishingPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.service.RodService;

/**
 * Command for administrators to receive a special admin fishing rod.
 */
public class AdminRodCommand implements CommandExecutor {

  private final RodService rodService;

  public AdminRodCommand(RodService rodService) {
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
    rodService.giveAdminRod(player);
    player.sendMessage("Admin fishing rod given.");
    return true;
  }
}
