package org.maks.fishingPlugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.service.Awarder;
import org.maks.fishingPlugin.service.LevelService;
import org.maks.fishingPlugin.service.LootService;
import org.maks.fishingPlugin.service.QteService;
import org.maks.fishingPlugin.service.QuestChainService;

/**
 * Listener replacing vanilla fishing drops with custom loot.
 */
public class FishingListener implements Listener {

  private final LootService lootService;
  private final Awarder awarder;
  private final LevelService levelService;
  private final QteService qteService;
  private final QuestChainService questService;
  private final int requiredLevel;

  public FishingListener(LootService lootService, Awarder awarder, LevelService levelService,
      QteService qteService, QuestChainService questService, int requiredLevel) {
    this.lootService = lootService;
    this.awarder = awarder;
    this.levelService = levelService;
    this.qteService = qteService;
    this.questService = questService;
    this.requiredLevel = requiredLevel;
  }

  @EventHandler
  public void onFish(PlayerFishEvent event) {
    Player player = event.getPlayer();
    if (event.getState() == PlayerFishEvent.State.FISHING) {
      if (player.getLevel() < requiredLevel) {
        player.sendMessage("You need level " + requiredLevel + " to fish.");
        event.setCancelled(true);
      }
      return;
    }
    if (event.getState() == PlayerFishEvent.State.BITE) {
      qteService.start(player);
      return;
    }
    if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
      return;
    }
    event.setCancelled(true);
    if (!qteService.consume(player)) {
      player.sendMessage("Branie uciekło!");
      return;
    }
    int rodLevel = levelService.getLevel(player);
    LootEntry loot = lootService.roll(rodLevel);
    Awarder.AwardResult res = awarder.give(player, loot);
    if (res.item() != null) {
      double kg = res.weightG() / 1000.0;
      player.sendMessage("You caught a fish weighing " + String.format("%.1f", kg) + "kg");
      int before = rodLevel;
      int after = levelService.awardCatchExp(player, kg, true);
      if (after > before) {
        player.sendMessage("Your fishing rod leveled up to " + after + "!");
      }
      questService.onCatch(player);
    }
  }
}
