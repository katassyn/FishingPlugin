package org.maks.fishingPlugin.gui;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import org.maks.fishingPlugin.data.QuestRepo;
import org.maks.fishingPlugin.model.QuestStage;
import org.maks.fishingPlugin.service.QuestChainService;
import org.maks.fishingPlugin.util.ItemSerialization;

/** Inventory based quest reward editor for administrators. */
public class AdminQuestEditorMenu implements Listener {

  private final QuestChainService questService;
  private final QuestRepo questRepo;
  private final JavaPlugin plugin;

  /** Reward item editors by player. */
  private final Map<UUID, QuestStage> itemEditors = new HashMap<>();

  public AdminQuestEditorMenu(JavaPlugin plugin, QuestChainService questService, QuestRepo questRepo) {
    this.plugin = plugin;
    this.questService = questService;
    this.questRepo = questRepo;
  }

  private Inventory createInventory() {
    Map<Integer, QuestStage> map = new HashMap<>();
    java.util.List<QuestStage> stages = questService.getStages();
    int size = Math.min(54, Math.max(9, ((stages.size() + 8) / 9) * 9));
    Inventory inv = Bukkit.createInventory(new Holder(map), size, "Quest Editor");
    int slot = 0;
    for (QuestStage stage : stages) {
      if (slot >= size) {
        break;
      }
      ItemStack item = new ItemStack(Material.PAPER);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.text(stage.title()));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Stage: " + stage.stage()));
        if (!stage.lore().isEmpty()) {
          lore.add(Component.text(stage.lore()));
        }
        lore.add(Component.text("Goal: " + stage.goalType() + " " + stage.goal()));
        switch (stage.rewardType()) {
          case MONEY -> lore.add(Component.text(
              "Reward: $" + String.format("%.0f", stage.reward())));
          case COMMAND -> lore.add(Component.text(
              "Reward: /" + stage.rewardData()));
          case ITEM -> lore.add(Component.text("Reward: Item"));
        }
        lore.add(Component.text("Left: edit reward"));
        lore.add(Component.text("Right/Shift-Right goal ±10"));
        meta.lore(lore);
        item.setItemMeta(meta);
      }
      inv.setItem(slot, item);
      map.put(slot, stage);
      slot++;
    }
    return inv;
  }

  /** Open the quest editor menu. */
  public void open(Player player) {
    player.openInventory(createInventory());
  }

  private void save(QuestStage updated, Player player) {
    questService.updateStage(updated);
    try {
      questRepo.upsert(updated);
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
    }
  }

  private void openItemEditor(Player player, QuestStage stage) {
    Inventory inv = Bukkit.createInventory(new ItemEditorHolder(), 27,
        "Reward for stage " + stage.stage());
    ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
    ItemMeta cm = confirm.getItemMeta();
    if (cm != null) {
      cm.displayName(Component.text("Confirm"));
      confirm.setItemMeta(cm);
    }
    inv.setItem(26, confirm);
    if (stage.rewardType() == QuestStage.RewardType.ITEM && !stage.rewardData().isEmpty()) {
      try {
        ItemStack[] items = ItemSerialization.fromBase64List(stage.rewardData());
        for (int i = 0; i < Math.min(items.length, 26); i++) {
          inv.setItem(i, items[i]);
        }
      } catch (Exception ignored) {
      }
    }
    itemEditors.put(player.getUniqueId(), stage);
    player.openInventory(inv);
    player.sendMessage(
        "Place reward items in the inventory and click the green block to confirm.");
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (event.getInventory().getHolder() instanceof Holder holder) {
      event.setCancelled(true);
      Player player = (Player) event.getWhoClicked();
      QuestStage stage = holder.map.get(event.getRawSlot());
      if (stage == null) {
        return;
      }

      ClickType click = event.getClick();
      switch (click) {
        case LEFT -> {
          if (stage.rewardType() != QuestStage.RewardType.ITEM) {
            stage = new QuestStage(stage.stage(), stage.title(), stage.lore(), stage.goalType(),
                stage.goal(), QuestStage.RewardType.ITEM, stage.reward(), stage.rewardData());
            save(stage, player);
          }
          openItemEditor(player, stage);
        }
        case RIGHT, SHIFT_RIGHT -> {
          int delta = click == ClickType.SHIFT_RIGHT ? -10 : 10;
          int newGoal = Math.max(0, stage.goal() + delta);
          QuestStage updated = new QuestStage(stage.stage(), stage.title(), stage.lore(),
              stage.goalType(), newGoal, stage.rewardType(), stage.reward(), stage.rewardData());
          save(updated, player);
          player.openInventory(createInventory());
        }
        default -> {
          // ignore
        }
      }
      return;
    }

    if (event.getView().getTopInventory().getHolder() instanceof ItemEditorHolder) {
      Player player = (Player) event.getWhoClicked();
      if (event.getClickedInventory() != null
          && event.getClickedInventory().getHolder() instanceof ItemEditorHolder) {
        int slot = event.getRawSlot();
        if (slot == 26) {
          event.setCancelled(true);
          QuestStage stage = itemEditors.remove(player.getUniqueId());
          if (stage != null) {
            Inventory top = event.getView().getTopInventory();
            java.util.List<ItemStack> items = new java.util.ArrayList<>();
            for (int i = 0; i < 26; i++) {
              ItemStack item = top.getItem(i);
              if (item != null && !item.getType().isAir()) {
                items.add(item);
              }
            }
            String data = "";
            if (!items.isEmpty()) {
              data = ItemSerialization.toBase64(items.toArray(new ItemStack[0]));
            }
            QuestStage updated = new QuestStage(stage.stage(), stage.title(), stage.lore(),
                stage.goalType(), stage.goal(), QuestStage.RewardType.ITEM, stage.reward(), data);
            save(updated, player);
            player.sendMessage("Reward saved for stage " + stage.stage());
          }
          player.closeInventory();
        }
      }
    }
  }
  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof ItemEditorHolder)) {
      return;
    }
    Player player = (Player) event.getPlayer();
    itemEditors.remove(player.getUniqueId());
    Bukkit.getScheduler().runTask(plugin, () -> open(player));
  }

  private static class Holder implements InventoryHolder {
    final Map<Integer, QuestStage> map;
    Holder(Map<Integer, QuestStage> map) {
      this.map = map;
    }
    @Override
    public Inventory getInventory() {
      return null;
    }
  }

  private static class ItemEditorHolder implements InventoryHolder {
    @Override
    public Inventory getInventory() {
      return null;
    }
  }
}

