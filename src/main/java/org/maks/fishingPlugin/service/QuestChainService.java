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
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.fishingPlugin.data.QuestProgressRepo;
import org.maks.fishingPlugin.data.QuestRepo;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
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
  private final NamespacedKey qualityKey;

  public QuestChainService(Economy economy, QuestRepo questRepo,
      QuestProgressRepo progressRepo, JavaPlugin plugin) {
    this.economy = economy;
    this.questRepo = questRepo;
    this.progressRepo = progressRepo;
    this.logger = plugin.getLogger();
    this.qualityKey = new NamespacedKey(plugin, "quality");
    loadDefinitions(plugin);
  }

  private void loadDefinitions(JavaPlugin plugin) {
    try {
      List<QuestStage> fromDb = questRepo.findAll();
      if (fromDb.size() == 42) {
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

  /** Call when a player catches something. */
  public void onCatch(Player player, LootEntry loot,
      double weightG, ItemStack item) {
    QuestProgress p = loadProgress(player.getUniqueId());
    if (p.stage() >= stages.size()) {
      return; // all done
    }
    QuestStage stage = stages.get(p.stage());
    boolean progressed = false;
    switch (stage.goalType()) {
      case CATCH -> {
        if (loot.category() == Category.FISH) {
          p = new QuestProgress(p.playerUuid(), p.stage(), p.count() + 1);
          progressed = true;
        }
      }
      case WEIGHT -> {
        if (loot.category() == Category.FISH) {
          int add = (int) Math.round(weightG);
          p = new QuestProgress(p.playerUuid(), p.stage(), p.count() + add);
          progressed = true;
        }
      }
      case CHEST -> {
        if (loot.category() == Category.FISHERMAN_CHEST) {
          p = new QuestProgress(p.playerUuid(), p.stage(), p.count() + 1);
          progressed = true;
        }
      }
      case MAP -> {
        if (loot.category() == Category.TREASURE_MAP) {
          p = new QuestProgress(p.playerUuid(), p.stage(), p.count() + 1);
          progressed = true;
        }
      }
      case RUNE -> {
        if (loot.category() == Category.RUNE) {
          p = new QuestProgress(p.playerUuid(), p.stage(), p.count() + 1);
          progressed = true;
        }
      }
      case TREASURE -> {
        if (loot.category() == Category.TREASURE) {
          p = new QuestProgress(p.playerUuid(), p.stage(), p.count() + 1);
          progressed = true;
        }
      }
      case RARE_PUFFERFISH -> {
        if (loot.category() == Category.FISH && loot.key().toLowerCase().contains("puffer")) {
          String qual = "";
          if (item != null && item.getItemMeta() != null) {
            var pdc = item.getItemMeta().getPersistentDataContainer();
            String q = pdc.get(qualityKey, PersistentDataType.STRING);
            if (q != null) {
              qual = q;
            }
          }
          if ("S".equalsIgnoreCase(qual)) {
            p = new QuestProgress(p.playerUuid(), p.stage(), p.count() + 1);
            progressed = true;
          }
        }
      }
      default -> {
        // ignore other goal types
      }
    }
    if (progressed) {
      progress.put(player.getUniqueId(), p);
      saveProgress(p);
      if (p.count() >= stage.goal()) {
        player.sendMessage(
            "Quest stage " + stage.stage() + " complete! Open the quest menu to claim reward.");
      }
    }
  }

  /** Call when a player earns money from quick selling. */
  public void onQuickSell(Player player, double amount) {
    QuestProgress p = loadProgress(player.getUniqueId());
    if (p.stage() >= stages.size()) {
      return;
    }
    QuestStage stage = stages.get(p.stage());
    if (stage.goalType() == QuestStage.GoalType.SELL) {
      int add = (int) Math.round(amount);
      p = new QuestProgress(p.playerUuid(), p.stage(), p.count() + add);
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
      int remaining = stage.goal() - p.count();
      String msg = switch (stage.goalType()) {
        case CATCH -> remaining + " more fish";
        case SELL -> "$" + remaining + " more from quick selling";
        case WEIGHT -> remaining + " g more total weight";
        case CHEST -> remaining + " more Fisherman's Chests";
        case MAP -> remaining + " more Treasure Maps";
        case RUNE -> remaining + " more Runes";
        case TREASURE -> remaining + " more Treasures";
        case RARE_PUFFERFISH -> remaining + " more rare pufferfish";
      };
      player.sendMessage("Need " + msg + " to finish quest stage " + stage.stage());
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
