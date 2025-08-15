package org.maks.fishingPlugin.service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.fishingPlugin.data.QuestProgressRepo;
import org.maks.fishingPlugin.data.QuestRepo;
import org.maks.fishingPlugin.model.QuestProgress;
import org.maks.fishingPlugin.model.QuestStage;
import org.maks.fishingPlugin.util.ItemSerialization;

/**
 * Quest chain service with persistent progress and definitions loaded from DB or YAML.
 */
public class QuestChainService {

  private final Economy economy;
  private final QuestRepo questRepo;
  private final QuestProgressRepo progressRepo;
  private final Logger logger;
  private final List<QuestStage> stages = new ArrayList<>();
  private final Map<UUID, QuestProgress> progress = new HashMap<>();

  public QuestChainService(Economy economy, QuestRepo questRepo,
      QuestProgressRepo progressRepo, JavaPlugin plugin) {
    this.economy = economy;
    this.questRepo = questRepo;
    this.progressRepo = progressRepo;
    this.logger = plugin.getLogger();
    loadDefinitions(plugin);
  }

  private void loadDefinitions(JavaPlugin plugin) {
    try {
      List<QuestStage> fromDb = questRepo.findAll();
      if (fromDb.size() == 21) {
        stages.addAll(fromDb);
        return;
      }
    } catch (SQLException e) {
      logger.warning("Failed to load quest stages from DB: " + e.getMessage());
    }
    try (InputStream is = plugin.getResource("quests.yml")) {
      if (is == null) {
        logger.warning("quests.yml not found");
        return;
      }
      YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(is));
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> list = (List<Map<String, Object>>) (List<?>) cfg.getMapList("quests");
      for (Map<String, Object> map : list) {
        int stage = ((Number) map.get("stage")).intValue();
        String title = String.valueOf(map.getOrDefault("title", "Stage " + stage));
        String lore = String.valueOf(map.getOrDefault("lore", ""));
        String gtStr = String.valueOf(map.getOrDefault("goalType", "CATCH"));
        QuestStage.GoalType goalType =
            QuestStage.GoalType.valueOf(gtStr.toUpperCase());
        int goal = ((Number) map.get("goal")).intValue();
        String rtStr = String.valueOf(map.getOrDefault("rewardType", "MONEY"));
        QuestStage.RewardType rewardType =
            QuestStage.RewardType.valueOf(rtStr.toUpperCase());
        double reward = ((Number) map.getOrDefault("reward", 0)).doubleValue();
        String rewardData = String.valueOf(map.getOrDefault("rewardData", ""));
        QuestStage qs =
            new QuestStage(stage, title, lore, goalType, goal, rewardType, reward, rewardData);
        stages.add(qs);
        try {
          questRepo.upsert(qs);
        } catch (SQLException e) {
          logger.warning("Failed to persist quest stage " + stage + ": " + e.getMessage());
        }
      }
      stages.sort(Comparator.comparingInt(QuestStage::stage));
    } catch (Exception e) {
      logger.warning("Failed to load quest stages from YAML: " + e.getMessage());
    }
  }

  public List<QuestStage> getStages() {
    return List.copyOf(stages);
  }

  /** Insert or update a quest stage definition. */
  public void updateStage(QuestStage stage) {
    boolean replaced = false;
    for (int i = 0; i < stages.size(); i++) {
      if (stages.get(i).stage() == stage.stage()) {
        stages.set(i, stage);
        replaced = true;
        break;
      }
    }
    if (!replaced) {
      stages.add(stage);
    }
    stages.sort(Comparator.comparingInt(QuestStage::stage));
    try {
      questRepo.upsert(stage);
    } catch (SQLException e) {
      logger.warning("Failed to persist quest stage " + stage.stage() + ": " + e.getMessage());
    }
  }

  private QuestProgress loadProgress(UUID uuid) {
    return progress.computeIfAbsent(uuid, u -> {
      try {
        return progressRepo.find(u).orElse(new QuestProgress(u, 0, 0));
      } catch (SQLException e) {
        logger.warning("Failed to load quest progress: " + e.getMessage());
        return new QuestProgress(u, 0, 0);
      }
    });
  }

  private void saveProgress(QuestProgress progress) {
    try {
      progressRepo.upsert(progress);
    } catch (SQLException e) {
      logger.warning("Failed to save quest progress: " + e.getMessage());
    }
  }

  /** Call when a player catches a fish. */
  public void onCatch(Player player) {
    QuestProgress p = loadProgress(player.getUniqueId());
    if (p.stage() >= stages.size()) {
      return; // all done
    }
    QuestStage stage = stages.get(p.stage());
    if (stage.goalType() == QuestStage.GoalType.CATCH) {
      p = new QuestProgress(p.playerUuid(), p.stage(), p.count() + 1);
      progress.put(player.getUniqueId(), p);
      saveProgress(p);
      if (p.count() >= stage.goal()) {
        player.sendMessage(
            "Quest stage " + stage.stage() + " complete! Open the quest menu to claim reward.");
      }
    }
  }

  public QuestProgress getProgress(Player player) {
    return loadProgress(player.getUniqueId());
  }

  public QuestStage getCurrentStage(int index) {
    if (index < 0 || index >= stages.size()) {
      return null;
    }
    return stages.get(index);
  }

  /** Claim reward or show progress if not finished. */
  public void claim(Player player) {
    QuestProgress p = loadProgress(player.getUniqueId());
    if (p.stage() >= stages.size()) {
      player.sendMessage("All quests completed.");
      return;
    }
    QuestStage stage = stages.get(p.stage());
    if (p.count() < stage.goal()) {
      player.sendMessage(
          "Catch " + (stage.goal() - p.count()) + " more fish to finish quest stage "
              + stage.stage());
      return;
    }
    switch (stage.rewardType()) {
      case MONEY -> {
        economy.depositPlayer(player, stage.reward());
        player.sendMessage(
            "Received $" + String.format("%.0f", stage.reward())
                + " for completing quest stage " + stage.stage());
      }
      case COMMAND -> {
        String cmd = stage.rewardData().replace("%player%", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        player.sendMessage("Command reward executed for quest stage " + stage.stage());
      }
      case ITEM -> {
        try {
          ItemStack item = ItemSerialization.fromBase64(stage.rewardData());
          player.getInventory().addItem(item);
          player.sendMessage("Item reward received for quest stage " + stage.stage());
        } catch (Exception e) {
          player.sendMessage("Failed to give item reward: " + e.getMessage());
        }
      }
    }
    p = new QuestProgress(p.playerUuid(), p.stage() + 1, 0);
    progress.put(player.getUniqueId(), p);
    saveProgress(p);
  }

  public boolean isCompleted(Player player) {
    return loadProgress(player.getUniqueId()).stage() >= stages.size();
  }
}
