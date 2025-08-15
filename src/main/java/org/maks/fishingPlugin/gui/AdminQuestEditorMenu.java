package org.maks.fishingPlugin.gui;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import org.maks.fishingPlugin.data.QuestRepo;
import org.maks.fishingPlugin.model.QuestStage;
import org.maks.fishingPlugin.service.QuestChainService;

/** Inventory based quest reward editor for administrators. */
public class AdminQuestEditorMenu implements Listener {

  private final QuestChainService questService;
  private final QuestRepo questRepo;

  public AdminQuestEditorMenu(QuestChainService questService, QuestRepo questRepo) {
    this.questService = questService;
    this.questRepo = questRepo;
  }

  private Inventory createInventory() {
    Map<Integer, QuestStage> map = new HashMap<>();
    Inventory inv = Bukkit.createInventory(new Holder(map), 27, "Quest Editor");
    int slot = 0;
    for (QuestStage stage : questService.getStages()) {
      ItemStack item = new ItemStack(Material.PAPER);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.text("Stage " + stage.stage()));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Goal: " + stage.goal()));
        lore.add(Component.text("Reward: " + String.format("%.0f", stage.reward())));
        lore.add(Component.text("Left-click +10, Right-click -10"));
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
    double delta = event.getClick() == ClickType.RIGHT ? -10.0 : 10.0;
    adjust(stage, delta, player);
    player.openInventory(createInventory());
  }

  private void adjust(QuestStage stage, double delta, Player player) {
    double newReward = Math.max(0, stage.reward() + delta);
    QuestStage updated = new QuestStage(stage.stage(), stage.goal(), newReward);
    questService.updateStage(updated);
    try {
      questRepo.upsert(updated);
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
    }
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
}

