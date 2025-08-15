package org.maks.fishingPlugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.service.Awarder;
import org.maks.fishingPlugin.service.LevelService;
import org.maks.fishingPlugin.service.LootService;
import java.util.concurrent.ThreadLocalRandom;
import org.maks.fishingPlugin.service.QteService;
import org.maks.fishingPlugin.service.QuestChainService;
import org.maks.fishingPlugin.service.AntiCheatService;

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
  private final AntiCheatService antiCheat;
  private final double dropMultiplier;

  public FishingListener(LootService lootService, Awarder awarder, LevelService levelService,
      QteService qteService, QuestChainService questService, int requiredLevel,
      AntiCheatService antiCheat, double dropMultiplier) {
    this.lootService = lootService;
    this.awarder = awarder;
    this.levelService = levelService;
    this.qteService = qteService;
    this.questService = questService;
    this.requiredLevel = requiredLevel;
    this.antiCheat = antiCheat;
    this.dropMultiplier = dropMultiplier;
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
      qteService.start(player); // start QTE with randomized timing
      return;
    }
    if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
      return;
    }
    event.setCancelled(true);
    QteService.Result qteResult = qteService.consume(player);
    if (!qteResult.success()) {
      player.sendMessage("The fish got away!");
      return;
    }
    boolean penalized = antiCheat.consumeFlag(player.getUniqueId());
    if (penalized && ThreadLocalRandom.current().nextDouble() > dropMultiplier) {
      player.sendMessage("Suspicious clicks - you caught nothing.");
      return;
    }
    int rodLevel = levelService.getLevel(player);
    LootEntry loot = lootService.roll(rodLevel);
    Awarder.AwardResult res = awarder.give(player, loot);
    if (res.item() != null) {
      double kg = res.weightG() / 1000.0;
      player.sendMessage("You caught a fish weighing " + String.format("%.1f", kg) + "kg");
      int before = rodLevel;
      int after = levelService.awardCatchExp(player, kg, qteResult.perfect());
      if (after > before) {
        player.sendMessage("Your fishing rod leveled up to " + after + "!");
      }
      questService.onCatch(player);
    }
  }
}
