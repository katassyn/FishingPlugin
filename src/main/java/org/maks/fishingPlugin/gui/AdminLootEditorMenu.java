package org.maks.fishingPlugin.gui;

import java.sql.SQLException;
import java.util.EnumMap;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import org.maks.fishingPlugin.data.LootRepo;
import org.maks.fishingPlugin.data.ParamRepo;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.model.ScaleConf;
import org.maks.fishingPlugin.model.ScaleMode;
import org.maks.fishingPlugin.service.LootService;
import org.maks.fishingPlugin.service.QuickSellService;
import org.maks.fishingPlugin.util.ItemSerialization;

/** Inventory based admin editor for loot and scaling. */
public class AdminLootEditorMenu implements Listener {

  private final JavaPlugin plugin;
  private final LootService lootService;
  private final LootRepo lootRepo;
  private final ParamRepo paramRepo;
  private final QuickSellService quickSellService;
  private final AdminQuestEditorMenu questMenu;

  /** Pending chat editors mapped by player for economy parameters. */
  private final Map<UUID, Consumer<String>> editors = new HashMap<>();

  public AdminLootEditorMenu(JavaPlugin plugin, LootService lootService, LootRepo lootRepo,
      ParamRepo paramRepo, QuickSellService quickSellService, AdminQuestEditorMenu questMenu) {
    this.plugin = plugin;
    this.lootService = lootService;
    this.lootRepo = lootRepo;
    this.paramRepo = paramRepo;
    this.quickSellService = quickSellService;
    this.questMenu = questMenu;
  }

  enum Type { MAIN, WEIGHTS, SCALING, ECON }

  private ItemStack button(Material mat, String name) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(name));
      item.setItemMeta(meta);
    }
    return item;
  }

  private Inventory mainInv() {
    Inventory inv = Bukkit.createInventory(new Holder(Type.MAIN), 27, "Admin Editor");
    inv.setItem(10, button(Material.GREEN_WOOL, "Add From Hand"));
    inv.setItem(12, button(Material.ANVIL, "Edit Weights"));
    inv.setItem(14, button(Material.BOOK, "Edit Scaling"));
    inv.setItem(16, button(Material.PAPER, "Edit Quests"));
    inv.setItem(20, button(Material.SUNFLOWER, "Edit Economy"));
    return inv;
  }

  private Inventory weightsInv() {
    Map<Integer, LootEntry> map = new HashMap<>();
    Inventory inv = Bukkit.createInventory(new Holder(Type.WEIGHTS, map), 54, "Weights");
    int slot = 0;
    for (LootEntry e : lootService.getEntries()) {
      ItemStack item;
      try {
        item = ItemSerialization.fromBase64(e.itemBase64());
      } catch (Exception ex) {
        item = new ItemStack(Material.PAPER);
      }
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.text(e.key()));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Weight: " + String.format("%.2f", e.baseWeight())));
        lore.add(Component.text("Left +1, Right -1"));
        meta.lore(lore);
        item.setItemMeta(meta);
      }
      inv.setItem(slot, item);
      map.put(slot, e);
      slot++;
      if (slot >= 53) break;
    }
    inv.setItem(53, button(Material.BARRIER, "Back"));
    return inv;
  }

  private Inventory economyInv() {
    Inventory inv = Bukkit.createInventory(new Holder(Type.ECON), 27, "Economy");
    ItemStack mult = new ItemStack(Material.PAPER);
    ItemMeta mMeta = mult.getItemMeta();
    if (mMeta != null) {
      mMeta.displayName(Component.text("Global Multiplier"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(String.format("%.2f", quickSellService.globalMultiplier())));
      lore.add(Component.text("L +0.1, R -0.1"));
      mMeta.lore(lore);
      mult.setItemMeta(mMeta);
    }
    inv.setItem(10, mult);

    ItemStack tax = new ItemStack(Material.PAPER);
    ItemMeta tMeta = tax.getItemMeta();
    if (tMeta != null) {
      tMeta.displayName(Component.text("QuickSell Tax"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(String.format("%.2f", quickSellService.tax())));
      lore.add(Component.text("L +0.01, R -0.01"));
      tMeta.lore(lore);
      tax.setItemMeta(tMeta);
    }
    inv.setItem(12, tax);

    ItemStack symbol = new ItemStack(Material.NAME_TAG);
    ItemMeta sMeta = symbol.getItemMeta();
    if (sMeta != null) {
      sMeta.displayName(Component.text("Currency Symbol"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(quickSellService.currencySymbol()));
      lore.add(Component.text("Click to edit"));
      sMeta.lore(lore);
      symbol.setItemMeta(sMeta);
    }
    inv.setItem(14, symbol);

    inv.setItem(26, button(Material.BARRIER, "Back"));
    return inv;
  }

  private Inventory scalingInv() {
    Map<Integer, Category> map = new HashMap<>();
    Inventory inv = Bukkit.createInventory(new Holder(Type.SCALING, map), 54, "Scaling");
    int slot = 0;
    for (Category cat : Category.values()) {
      ScaleConf conf = lootService.getScale(cat);
      if (conf == null) {
        conf = new ScaleConf(ScaleMode.EXP, 0, 0);
      }
      ItemStack item = new ItemStack(Material.PAPER);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.text(cat.name()));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Mode: " + conf.mode()));
        lore.add(Component.text(String.format("A: %.2f", conf.a())));
        lore.add(Component.text(String.format("K: %.2f", conf.k())));
        lore.add(Component.text("L-click mode, R +/-A, M +/-K"));
        meta.lore(lore);
        item.setItemMeta(meta);
      }
      inv.setItem(slot, item);
      map.put(slot, cat);
      slot++;
    }
    inv.setItem(53, button(Material.BARRIER, "Back"));
    return inv;
  }

  /** Open the admin menu. */
  public void open(Player player) {
    player.openInventory(mainInv());
  }

  private void openWeights(Player player) {
    player.openInventory(weightsInv());
  }

  private void openScaling(Player player) {
    player.openInventory(scalingInv());
  }

  private void openEconomy(Player player) {
    player.openInventory(economyInv());
  }

  private void addFromHand(Player player) {
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      player.sendMessage("Hold an item in your hand.");
      return;
    }
    String key = "hand_" + UUID.randomUUID();
    LootEntry entry = new LootEntry(key, Category.FISH, 1.0, 0, false, 0.0,
        0.0, 1.0, 1.0, 1.0, 1.0, 1.0, 100.0, 200.0, ItemSerialization.toBase64(item));
    lootService.addEntry(entry);
    try {
      lootRepo.upsert(entry);
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
    }
    player.sendMessage("Added loot entry " + key);
  }

  private void adjustWeight(Player player, LootEntry e, double delta) {
    double newWeight = Math.max(0.0, e.baseWeight() + delta);
    LootEntry updated = new LootEntry(e.key(), e.category(), newWeight, e.minRodLevel(), e.broadcast(),
        e.priceBase(), e.pricePerKg(), e.payoutMultiplier(), e.qualitySWeight(), e.qualityAWeight(),
        e.qualityBWeight(), e.qualityCWeight(), e.minWeightG(), e.maxWeightG(), e.itemBase64());
    lootService.updateEntry(updated);
    try {
      lootRepo.upsert(updated);
    } catch (SQLException ex) {
      player.sendMessage("DB error: " + ex.getMessage());
    }
  }

  private void cycleMode(Player player, Category cat, ScaleConf conf) {
    ScaleMode mode = conf.mode() == ScaleMode.EXP ? ScaleMode.POLY : ScaleMode.EXP;
    ScaleConf updated = new ScaleConf(mode, conf.a(), conf.k());
    saveScale(cat, updated, player);
  }

  private void adjustA(Player player, Category cat, ScaleConf conf, double delta) {
    ScaleConf updated = new ScaleConf(conf.mode(), conf.a() + delta, conf.k());
    saveScale(cat, updated, player);
  }

  private void adjustK(Player player, Category cat, ScaleConf conf, double delta) {
    ScaleConf updated = new ScaleConf(conf.mode(), conf.a(), conf.k() + delta);
    saveScale(cat, updated, player);
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

  private void adjustMultiplier(Player player, double delta) {
    double newVal = Math.max(0.0, quickSellService.globalMultiplier() + delta);
    quickSellService.setGlobalMultiplier(newVal);
    try {
      paramRepo.set("global_multiplier", String.valueOf(newVal));
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
    }
  }

  private void adjustTax(Player player, double delta) {
    double newVal = Math.max(0.0, Math.min(1.0, quickSellService.tax() + delta));
    quickSellService.setTax(newVal);
    try {
      paramRepo.set("quicksell_tax", String.valueOf(newVal));
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
    }
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder holder)) {
      return;
    }
    event.setCancelled(true);
    Player player = (Player) event.getWhoClicked();
    switch (holder.type) {
      case MAIN -> {
        int slot = event.getRawSlot();
        if (slot == 10) {
          addFromHand(player);
          open(player);
        } else if (slot == 12) {
          openWeights(player);
        } else if (slot == 14) {
          openScaling(player);
        } else if (slot == 16) {
          questMenu.open(player);
        } else if (slot == 20) {
          openEconomy(player);
        }
      }
      case WEIGHTS -> {
        if (event.getRawSlot() == 53) {
          open(player);
          return;
        }
        LootEntry e = holder.weightMap.get(event.getRawSlot());
        if (e != null) {
          double delta = event.getClick() == ClickType.RIGHT ? -1.0 : 1.0;
          adjustWeight(player, e, delta);
          openWeights(player);
        }
      }
      case SCALING -> {
        if (event.getRawSlot() == 53) {
          open(player);
          return;
        }
        Category cat = holder.scaleMap.get(event.getRawSlot());
        if (cat != null) {
          ScaleConf conf = lootService.getScale(cat);
          if (conf == null) {
            conf = new ScaleConf(ScaleMode.EXP, 0, 0);
          }
          ClickType ct = event.getClick();
          if (ct == ClickType.LEFT) {
            cycleMode(player, cat, conf);
          } else if (ct == ClickType.RIGHT) {
            adjustA(player, cat, conf, 0.1);
          } else if (ct == ClickType.SHIFT_RIGHT) {
            adjustA(player, cat, conf, -0.1);
          } else if (ct == ClickType.MIDDLE) {
            adjustK(player, cat, conf, 0.1);
          } else if (ct == ClickType.SHIFT_LEFT) {
            adjustK(player, cat, conf, -0.1);
          }
          openScaling(player);
        }
      }
      case ECON -> {
        int slot = event.getRawSlot();
        if (slot == 26) {
          open(player);
          return;
        }
        if (slot == 10) {
          double delta = event.getClick() == ClickType.RIGHT ? -0.1 : 0.1;
          adjustMultiplier(player, delta);
          openEconomy(player);
        } else if (slot == 12) {
          double delta = event.getClick() == ClickType.RIGHT ? -0.01 : 0.01;
          adjustTax(player, delta);
          openEconomy(player);
        } else if (slot == 14) {
          editors.put(player.getUniqueId(), msg -> {
            quickSellService.setCurrencySymbol(msg);
            try {
              paramRepo.set("currency_symbol", msg);
            } catch (SQLException e) {
              player.sendMessage("DB error: " + e.getMessage());
            }
          });
          player.closeInventory();
          player.sendMessage("Enter currency symbol in chat");
        }
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
      openEconomy(event.getPlayer());
    });
  }

  private static class Holder implements InventoryHolder {
    final Type type;
    final Map<Integer, LootEntry> weightMap;
    final Map<Integer, Category> scaleMap;
    Holder(Type type) {
      this(type, new HashMap<>(), new EnumMap<>(Category.class));
    }
    Holder(Type type, Map<Integer, LootEntry> weightMap) {
      this(type, weightMap, new EnumMap<>(Category.class));
    }
    Holder(Type type, Map<Integer, LootEntry> weightMap, Map<Integer, Category> scaleMap) {
      this.type = type;
      this.weightMap = weightMap;
      this.scaleMap = scaleMap;
    }
    @Override
    public Inventory getInventory() {
      return null;
    }
  }
}

