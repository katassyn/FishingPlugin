package org.maks.fishingPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.service.QuickSellService;
import org.maks.fishingPlugin.service.TeleportService;
import org.maks.fishingPlugin.service.QuestChainService;
import org.maks.fishingPlugin.service.LevelService;

/**
 * Basic command handler with a quick sell subcommand.
 */
public class FishingCommand implements CommandExecutor {

  private final QuickSellService quickSellService;
  private final TeleportService teleportService;
  private final QuestChainService questService;
  private final LevelService levelService;
  private final int requiredLevel;

  public FishingCommand(QuickSellService quickSellService, TeleportService teleportService,
      QuestChainService questService, LevelService levelService,
      int requiredLevel) {
    this.quickSellService = quickSellService;
    this.teleportService = teleportService;
    this.questService = questService;
    this.levelService = levelService;
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
    if (args.length > 0) {
      if (args[0].equalsIgnoreCase("sell")) {
        double amount = quickSellService.sellAll(player);
        player.sendMessage("Sold fish for " + quickSellService.currencySymbol()
            + String.format("%.2f", amount));
        return true;
      }
      if (args[0].equalsIgnoreCase("warp") && args.length > 1) {
        if (teleportService.teleport(args[1], player)) {
          player.sendMessage("Teleported to " + args[1]);
        } else {
          player.sendMessage("Unknown warp " + args[1]);
        }
        return true;
      }
      if (args[0].equalsIgnoreCase("quest")) {
        questService.claim(player);
        return true;
      }
    }
    player.sendMessage("Usage: /" + label + " sell|warp <key>|quest");
    return true;
  }
}
