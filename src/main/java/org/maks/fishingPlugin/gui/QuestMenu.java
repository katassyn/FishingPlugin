package org.maks.fishingPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import org.maks.fishingPlugin.model.QuestProgress;
import org.maks.fishingPlugin.model.QuestStage;
import org.maks.fishingPlugin.service.QuestChainService;

/**
 * Inventory based quest progress menu.
 */
public class QuestMenu implements Listener {

  private final QuestChainService questService;

  public QuestMenu(QuestChainService questService) {
    this.questService = questService;
  }

  private Inventory createInventory(Player player) {
    Inventory inv = Bukkit.createInventory(new Holder(), 27, "Quests");
    if (questService.isCompleted(player)) {
      ItemStack done = new ItemStack(Material.BOOK);
      ItemMeta meta = done.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.text("All quests completed"));
        done.setItemMeta(meta);
      }
      inv.setItem(13, done);
      return inv;
    }
    QuestProgress p = questService.getProgress(player);
    QuestStage stage = questService.getCurrentStage(p.stage());
    ItemStack info = new ItemStack(Material.PAPER);
    ItemMeta meta = info.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(stage.title()));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      if (!stage.lore().isEmpty()) {
        lore.add(Component.text(stage.lore()));
      }
      lore.add(Component.text("Progress: " + p.count() + "/" + stage.goal()));
      switch (stage.rewardType()) {
        case MONEY -> lore.add(
            Component.text("Reward: $" + String.format("%.0f", stage.reward())));
        case COMMAND -> lore.add(
            Component.text("Reward: /" + stage.rewardData()));
        case ITEM -> lore.add(Component.text("Reward: Item"));
      }
      meta.lore(lore);
      info.setItemMeta(meta);
    }
    inv.setItem(12, info);

    if (p.count() >= stage.goal()) {
      ItemStack claim = new ItemStack(Material.GOLD_INGOT);
      ItemMeta cm = claim.getItemMeta();
      if (cm != null) {
        cm.displayName(Component.text("Claim Reward"));
        claim.setItemMeta(cm);
      }
      inv.setItem(14, claim);
    } else {
      ItemStack keep = new ItemStack(Material.FISHING_ROD);
      ItemMeta km = keep.getItemMeta();
      if (km != null) {
        km.displayName(Component.text("Keep fishing..."));
        keep.setItemMeta(km);
      }
      inv.setItem(14, keep);
    }
    return inv;
  }

  /** Open the quest menu. */
  public void open(Player player) {
    player.openInventory(createInventory(player));
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder)) {
      return;
    }
    event.setCancelled(true);
    Player player = (Player) event.getWhoClicked();
    if (event.getRawSlot() == 14) {
      if (!questService.isCompleted(player)) {
        QuestProgress p = questService.getProgress(player);
        QuestStage stage = questService.getCurrentStage(p.stage());
        if (p.count() >= stage.goal()) {
          questService.claim(player);
        }
      }
      open(player);
    }
  }

  private static class Holder implements InventoryHolder {
    @Override
    public Inventory getInventory() {
      return null;
    }
  }
}

