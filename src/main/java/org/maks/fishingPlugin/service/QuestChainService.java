package org.maks.fishingPlugin.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.model.QuestStage;

/**
 * Minimal quest chain service with catch-count goals and money rewards.
 */
public class QuestChainService {

  private final Economy economy;
  private final List<QuestStage> stages = new ArrayList<>();
  private final Map<UUID, Progress> progress = new HashMap<>();

  public QuestChainService(Economy economy) {
    this.economy = economy;
  }

  /** Replace quest stages with definitions from storage. */
  public void setStages(List<QuestStage> stages) {
    this.stages.clear();
    this.stages.addAll(stages);
  }

  public List<QuestStage> getStages() {
    return List.copyOf(stages);
  }

  public void updateStage(QuestStage stage) {
    for (int i = 0; i < stages.size(); i++) {
      if (stages.get(i).stage() == stage.stage()) {
        stages.set(i, stage);
        return;
      }
    }
    stages.add(stage);
    stages.sort(java.util.Comparator.comparingInt(QuestStage::stage));
  }

  /** Call when a player catches a fish. */
  public void onCatch(Player player) {
    Progress p = progress.computeIfAbsent(player.getUniqueId(), u -> new Progress());
    if (p.stage >= stages.size()) {
      return; // all done
    }
    p.count++;
    QuestStage stage = stages.get(p.stage);
    if (p.count >= stage.goal()) {
      player.sendMessage(
          "Quest stage " + stage.stage() + " complete! Use /fishing quest to claim reward.");
    }
  }

  /** Claim reward or show progress if not finished. */
  public void claim(Player player) {
    Progress p = progress.computeIfAbsent(player.getUniqueId(), u -> new Progress());
    if (p.stage >= stages.size()) {
      player.sendMessage("All quests completed.");
      return;
    }
    QuestStage stage = stages.get(p.stage);
    if (p.count < stage.goal()) {
      player.sendMessage(
          "Catch " + (stage.goal() - p.count) + " more fish to finish quest stage " + stage.stage());
      return;
    }
    economy.depositPlayer(player, stage.reward());
    player.sendMessage(
        "Received $" + String.format("%.0f", stage.reward()) +
            " for completing quest stage " + stage.stage());
    p.stage++;
    p.count = 0;
  }

  private static class Progress {
    int stage = 0;
    int count = 0;
  }
}
