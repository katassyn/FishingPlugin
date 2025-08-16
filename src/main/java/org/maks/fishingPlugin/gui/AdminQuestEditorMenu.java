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
    ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    ItemMeta fm = filler.getItemMeta();
    if (fm != null) {
      fm.displayName(Component.text(" "));
      filler.setItemMeta(fm);
    }
    for (int i = 0; i < 27; i++) {
      inv.setItem(i, filler);
    }
    if (stage.rewardType() == QuestStage.RewardType.ITEM && !stage.rewardData().isEmpty()) {
      try {
        inv.setItem(13, ItemSerialization.fromBase64(stage.rewardData()));
      } catch (Exception ignored) {
      }
    }
    itemEditors.put(player.getUniqueId(), stage);
    player.openInventory(inv);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder holder)) {
      return;
    }
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
  }
  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof ItemEditorHolder)) {
      return;
    }
    Player player = (Player) event.getPlayer();
    QuestStage stage = itemEditors.remove(player.getUniqueId());
    if (stage == null) {
      return;
    }
    ItemStack item = event.getInventory().getItem(13);
    String data = "";
    if (item != null && !item.getType().isAir()) {
      data = ItemSerialization.toBase64(item);
    }
    QuestStage updated = new QuestStage(stage.stage(), stage.title(), stage.lore(),
        stage.goalType(), stage.goal(), QuestStage.RewardType.ITEM, stage.reward(), data);
    save(updated, player);
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

