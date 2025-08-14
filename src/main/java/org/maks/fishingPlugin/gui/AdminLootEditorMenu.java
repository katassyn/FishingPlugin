package org.maks.fishingPlugin.gui;

import java.sql.SQLException;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.maks.fishingPlugin.data.LootRepo;
import org.maks.fishingPlugin.data.ParamRepo;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.model.ScaleConf;
import org.maks.fishingPlugin.model.ScaleMode;
import org.maks.fishingPlugin.service.LootService;
import org.maks.fishingPlugin.util.ItemSerialization;

/** Chat-based admin editor for loot and scaling. */
public class AdminLootEditorMenu {

  private final LootService lootService;
  private final LootRepo lootRepo;
  private final ParamRepo paramRepo;
  private final AdminQuestEditorMenu questMenu;

  public AdminLootEditorMenu(LootService lootService, LootRepo lootRepo, ParamRepo paramRepo,
      AdminQuestEditorMenu questMenu) {
    this.lootService = lootService;
    this.lootRepo = lootRepo;
    this.paramRepo = paramRepo;
    this.questMenu = questMenu;
  }

  public void open(Player player) {
    Component menu = Component.text()
        .append(Component.text("Admin Editor").color(NamedTextColor.RED))
        .append(Component.newline())
        .append(Component.text("[Add From Hand]").color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.callback(a -> addFromHand(player))))
        .append(Component.newline())
        .append(Component.text("[Edit Weights]").color(NamedTextColor.YELLOW)
            .clickEvent(ClickEvent.callback(a -> openWeights(player))))
        .append(Component.newline())
        .append(Component.text("[Edit Scaling]").color(NamedTextColor.AQUA)
            .clickEvent(ClickEvent.callback(a -> openScaling(player))))
        .append(Component.newline())
        .append(Component.text("[Edit Quests]").color(NamedTextColor.GOLD)
            .clickEvent(ClickEvent.callback(a -> questMenu.open(player))))
        .build();
    player.sendMessage(menu);
  }

  private void addFromHand(Player player) {
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      player.sendMessage("Hold an item in your hand.");
      return;
    }
    String key = "hand_" + UUID.randomUUID();
    LootEntry entry = new LootEntry(key, Category.COMMON, 1.0, 0, false, 0.0,
        0.0, 1.0, 100.0, 200.0, ItemSerialization.toBase64(item));
    lootService.addEntry(entry);
    try {
      lootRepo.upsert(entry);
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
    }
    player.sendMessage("Added loot entry " + key);
  }

  private void openWeights(Player player) {
    Component menu = Component.text("Weights").color(NamedTextColor.YELLOW);
    for (LootEntry e : lootService.getEntries()) {
      Component line = Component.text()
          .append(Component.newline())
          .append(Component.text(e.key() + " = " + String.format("%.2f", e.baseWeight())))
          .append(Component.text(" [+]").color(NamedTextColor.GREEN)
              .clickEvent(ClickEvent.callback(a -> adjustWeight(player, e, 1.0))))
          .append(Component.text(" [-]").color(NamedTextColor.RED)
              .clickEvent(ClickEvent.callback(a -> adjustWeight(player, e, -1.0))))
          .build();
      menu = menu.append(line);
    }
    menu = menu.append(Component.newline()).append(Component.text("[Back]").color(NamedTextColor.GRAY)
        .clickEvent(ClickEvent.callback(a -> open(player))));
    player.sendMessage(menu);
  }

  private void adjustWeight(Player player, LootEntry e, double delta) {
    double newWeight = Math.max(0.0, e.baseWeight() + delta);
    LootEntry updated = new LootEntry(e.key(), e.category(), newWeight, e.minRodLevel(), e.broadcast(),
        e.priceBase(), e.pricePerKg(), e.payoutMultiplier(), e.minWeightG(), e.maxWeightG(), e.itemBase64());
    lootService.updateEntry(updated);
    try {
      lootRepo.upsert(updated);
    } catch (SQLException ex) {
      player.sendMessage("DB error: " + ex.getMessage());
    }
    openWeights(player);
  }

  private void openScaling(Player player) {
    Component menu = Component.text("Scaling").color(NamedTextColor.AQUA);
    for (Category cat : Category.values()) {
      ScaleConf conf = lootService.getScale(cat);
      if (conf == null) {
        conf = new ScaleConf(ScaleMode.EXP, 0, 0);
      }
      Component line = Component.text()
          .append(Component.newline())
          .append(Component.text(cat.name() + " " + conf.mode() + " a=" + conf.a() + " k=" + conf.k()))
          .append(Component.text(" [Mode]").color(NamedTextColor.YELLOW)
              .clickEvent(ClickEvent.callback(a -> cycleMode(player, cat, conf))))
          .append(Component.text(" [A+]").color(NamedTextColor.GREEN)
              .clickEvent(ClickEvent.callback(a -> adjustA(player, cat, conf, 0.1))))
          .append(Component.text(" [A-]").color(NamedTextColor.RED)
              .clickEvent(ClickEvent.callback(a -> adjustA(player, cat, conf, -0.1))))
          .append(Component.text(" [K+]").color(NamedTextColor.GREEN)
              .clickEvent(ClickEvent.callback(a -> adjustK(player, cat, conf, 0.1))))
          .append(Component.text(" [K-]").color(NamedTextColor.RED)
              .clickEvent(ClickEvent.callback(a -> adjustK(player, cat, conf, -0.1))))
          .build();
      menu = menu.append(line);
    }
    menu = menu.append(Component.newline()).append(Component.text("[Back]").color(NamedTextColor.GRAY)
        .clickEvent(ClickEvent.callback(a -> open(player))));
    player.sendMessage(menu);
  }

  private void cycleMode(Player player, Category cat, ScaleConf conf) {
    ScaleMode mode = conf.mode() == ScaleMode.EXP ? ScaleMode.POLY : ScaleMode.EXP;
    ScaleConf updated = new ScaleConf(mode, conf.a(), conf.k());
    saveScale(cat, updated, player);
    openScaling(player);
  }

  private void adjustA(Player player, Category cat, ScaleConf conf, double delta) {
    ScaleConf updated = new ScaleConf(conf.mode(), conf.a() + delta, conf.k());
    saveScale(cat, updated, player);
    openScaling(player);
  }

  private void adjustK(Player player, Category cat, ScaleConf conf, double delta) {
    ScaleConf updated = new ScaleConf(conf.mode(), conf.a(), conf.k() + delta);
    saveScale(cat, updated, player);
    openScaling(player);
  }

  private void saveScale(Category cat, ScaleConf conf, Player player) {
    lootService.setScale(cat, conf);
    try {
      paramRepo.set("scale_mode_" + cat.name(), conf.mode().name());
      paramRepo.set("scale_a_" + cat.name(), String.valueOf(conf.a()));
      paramRepo.set("scale_k_" + cat.name(), String.valueOf(conf.k()));
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
    }
  }
}
