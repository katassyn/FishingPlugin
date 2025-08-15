package org.maks.fishingPlugin.gui;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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

  /** Pending chat editors mapped by player. */
  private final Map<UUID, Consumer<String>> editors = new HashMap<>();
  /** Reward item editors by player. */
  private final Map<UUID, QuestStage> itemEditors = new HashMap<>();

  public AdminQuestEditorMenu(JavaPlugin plugin, QuestChainService questService, QuestRepo questRepo) {
    this.plugin = plugin;
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
        lore.add(Component.text("L/R goal ±10"));
        lore.add(Component.text("Shift+L/R edit reward"));
        lore.add(Component.text("Middle: cycle reward, Shift+Middle: cycle goal"));
        lore.add(Component.text("F:title Q:lore"));
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
      case LEFT, RIGHT -> {
        int delta = click == ClickType.RIGHT ? -10 : 10;
        int newGoal = Math.max(0, stage.goal() + delta);
        QuestStage updated = new QuestStage(stage.stage(), stage.title(), stage.lore(),
            stage.goalType(), newGoal, stage.rewardType(), stage.reward(), stage.rewardData());
        save(updated, player);
        player.openInventory(createInventory());
      }
      case SHIFT_LEFT, SHIFT_RIGHT -> {
        if (stage.rewardType() == QuestStage.RewardType.MONEY) {
          double delta = click == ClickType.SHIFT_RIGHT ? -10.0 : 10.0;
          double newReward = Math.max(0, stage.reward() + delta);
          QuestStage updated = new QuestStage(stage.stage(), stage.title(), stage.lore(),
              stage.goalType(), stage.goal(), stage.rewardType(), newReward, stage.rewardData());
          save(updated, player);
          player.openInventory(createInventory());
        } else if (stage.rewardType() == QuestStage.RewardType.COMMAND) {
          editors.put(player.getUniqueId(), msg -> {
            QuestStage updated = new QuestStage(stage.stage(), stage.title(), stage.lore(),
                stage.goalType(), stage.goal(), stage.rewardType(), stage.reward(), msg);
            save(updated, player);
          });
          player.closeInventory();
          player.sendMessage("Type command in chat (without /)");
        } else if (stage.rewardType() == QuestStage.RewardType.ITEM) {
          openItemEditor(player, stage);
        }
      }
      case MIDDLE -> {
        if (event.isShiftClick()) {
          QuestStage.GoalType[] vals = QuestStage.GoalType.values();
          QuestStage.GoalType next = vals[(stage.goalType().ordinal() + 1) % vals.length];
          QuestStage updated = new QuestStage(stage.stage(), stage.title(), stage.lore(),
              next, stage.goal(), stage.rewardType(), stage.reward(), stage.rewardData());
          save(updated, player);
        } else {
          QuestStage.RewardType[] vals = QuestStage.RewardType.values();
          QuestStage.RewardType next = vals[(stage.rewardType().ordinal() + 1) % vals.length];
          QuestStage updated = new QuestStage(stage.stage(), stage.title(), stage.lore(),
              stage.goalType(), stage.goal(), next, stage.reward(), stage.rewardData());
          save(updated, player);
        }
        player.openInventory(createInventory());
      }
      case SWAP_OFFHAND -> {
        editors.put(player.getUniqueId(), msg -> {
          QuestStage updated = new QuestStage(stage.stage(), msg, stage.lore(), stage.goalType(),
              stage.goal(), stage.rewardType(), stage.reward(), stage.rewardData());
          save(updated, player);
        });
        player.closeInventory();
        player.sendMessage("Enter title in chat");
      }
      case DROP -> {
        editors.put(player.getUniqueId(), msg -> {
          QuestStage updated = new QuestStage(stage.stage(), stage.title(), msg, stage.goalType(),
              stage.goal(), stage.rewardType(), stage.reward(), stage.rewardData());
          save(updated, player);
        });
        player.closeInventory();
        player.sendMessage("Enter lore in chat");
      }
      default -> {
        // ignore
      }
    }
  }

  @EventHandler
  public void onChat(AsyncPlayerChatEvent event) {
    Consumer<String> consumer = editors.remove(event.getPlayer().getUniqueId());
    if (consumer == null) {
      return;
    }
    event.setCancelled(true);
    String msg = event.getMessage();
    Bukkit.getScheduler().runTask(plugin, () -> {
      consumer.accept(msg);
      event.getPlayer().sendMessage("Updated.");
      open(event.getPlayer());
    });
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

