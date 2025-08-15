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
    Inventory inv = Bukkit.createInventory(new Holder(), 54, "Quests");
    ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    ItemMeta fm = filler.getItemMeta();
    if (fm != null) {
      fm.displayName(Component.text(" "));
      filler.setItemMeta(fm);
    }
    for (int i = 0; i < 54; i++) {
      inv.setItem(i, filler);
    }
    if (questService.isCompleted(player)) {
      ItemStack done = new ItemStack(Material.BOOK);
      ItemMeta meta = done.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.text("All quests completed"));
        done.setItemMeta(meta);
      }
      inv.setItem(22, done);
      return inv;
    }
    QuestProgress prog = questService.getProgress(player);
    java.util.List<QuestStage> stages = questService.getStages();
    for (int i = 0; i < stages.size() && i < 54; i++) {
      QuestStage stage = stages.get(i);
      ItemStack item;
      ItemMeta meta;
      if (i < prog.stage()) {
        item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        meta = item.getItemMeta();
        if (meta != null) {
          meta.displayName(Component.text(stage.title()));
          meta.lore(java.util.List.of(Component.text("Completed")));
          item.setItemMeta(meta);
        }
      } else if (i == prog.stage()) {
        boolean ready = prog.count() >= stage.goal();
        item = new ItemStack(ready ? Material.GOLD_INGOT : Material.PAPER);
        meta = item.getItemMeta();
        if (meta != null) {
          meta.displayName(Component.text(stage.title()));
          java.util.List<Component> lore = new java.util.ArrayList<>();
          if (!stage.lore().isEmpty()) {
            lore.add(Component.text(stage.lore()));
          }
          lore.add(Component.text("Progress: " + prog.count() + "/" + stage.goal()));
          switch (stage.rewardType()) {
            case MONEY -> lore.add(Component.text("Reward: $" +
                String.format("%.0f", stage.reward())));
            case COMMAND -> lore.add(Component.text("Reward: /" + stage.rewardData()));
            case ITEM -> lore.add(Component.text("Reward: Item"));
          }
          if (ready) {
            lore.add(Component.text("Click to claim"));
          }
          meta.lore(lore);
          item.setItemMeta(meta);
        }
      } else {
        item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        meta = item.getItemMeta();
        if (meta != null) {
          meta.displayName(Component.text("Locked"));
          item.setItemMeta(meta);
        }
      }
      inv.setItem(i, item);
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
    if (questService.isCompleted(player)) {
      return;
    }
    QuestProgress prog = questService.getProgress(player);
    int slot = event.getRawSlot();
    if (slot == prog.stage() && slot < questService.getStages().size()) {
      QuestStage stage = questService.getStages().get(slot);
      if (prog.count() >= stage.goal()) {
        questService.claim(player);
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

