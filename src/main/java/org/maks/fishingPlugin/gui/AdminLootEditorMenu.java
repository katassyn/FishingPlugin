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
import org.maks.fishingPlugin.data.MirrorItemRepo;
import org.maks.fishingPlugin.data.ParamRepo;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.model.MirrorItem;
import org.maks.fishingPlugin.model.ScaleConf;
import org.maks.fishingPlugin.model.ScaleMode;
import org.maks.fishingPlugin.service.LootService;
import org.maks.fishingPlugin.service.MirrorItemService;
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
  private final MirrorItemRepo mirrorItemRepo;
  private final MirrorItemService mirrorItemService;

  /** Pending chat editors mapped by player. */
  private final Map<UUID, Editor> editors = new HashMap<>();
  /** Mirror item editor state per player. */
  private final Map<UUID, MirrorData> mirrorEditors = new HashMap<>();
  /** New drop creation state per player. */
  private final Map<UUID, AddContext> addEditors = new HashMap<>();

  public AdminLootEditorMenu(JavaPlugin plugin, LootService lootService, LootRepo lootRepo,
      ParamRepo paramRepo, QuickSellService quickSellService, AdminQuestEditorMenu questMenu,
      MirrorItemRepo mirrorItemRepo, MirrorItemService mirrorItemService) {
    this.plugin = plugin;
    this.lootService = lootService;
    this.lootRepo = lootRepo;
    this.paramRepo = paramRepo;
    this.quickSellService = quickSellService;
    this.questMenu = questMenu;
    this.mirrorItemRepo = mirrorItemRepo;
    this.mirrorItemService = mirrorItemService;
  }

  enum Type {
    MAIN,
    ADD_ITEMS,
    ADD_CATS,
    ADD_WEIGHTS,
    WEIGHTS,
    CAT_WEIGHTS,
    SCALING,
    ECON,
    MIRROR_ADD,
    MIRROR_SELECT,
    ENTRY
  }

  private static final int[] PREVIEW_LEVELS = {0, 15, 30, 60, 100};

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
    inv.setItem(10, button(Material.GREEN_WOOL, "Add Drops"));
    inv.setItem(12, button(Material.ANVIL, "Edit Entry Weights"));
    inv.setItem(14, button(Material.BOOK, "Edit Scaling"));
    inv.setItem(16, button(Material.PAPER, "Edit Quests"));
    inv.setItem(18, button(Material.GLASS, "Add Mirror Item"));
    inv.setItem(20, button(Material.SUNFLOWER, "Edit Economy"));
    inv.setItem(22, button(Material.CHEST, "Edit Category Weights"));
    return inv;
  }

  private Inventory addItemsInv(AddContext ctx) {
    Inventory inv = Bukkit.createInventory(new Holder(Type.ADD_ITEMS, ctx), 54, "Place Items");
    inv.setItem(52, button(Material.ARROW, "Back"));
    inv.setItem(53, button(Material.LIME_WOOL, "Next"));
    return inv;
  }

  private Inventory addCatsInv(AddContext ctx) {
    Map<Integer, Integer> idx = new HashMap<>();
    Inventory inv = Bukkit.createInventory(new Holder(Type.ADD_CATS, null, new HashMap<>(), new HashMap<>(), idx, ctx), 54,
        "Select Categories");
    int slot = 0;
    for (int i = 0; i < ctx.items.size() && slot < 52; i++) {
      ItemStack item = ctx.items.get(i).clone();
      Category cat;
      if (ctx.categories.size() > i) {
        cat = ctx.categories.get(i);
      } else {
        cat = Category.FISH;
        ctx.categories.add(cat);
      }
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Category: " + cat.name()));
        lore.add(Component.text("Click to cycle"));
        meta.lore(lore);
        item.setItemMeta(meta);
      }
      inv.setItem(slot, item);
      idx.put(slot, i);
      slot++;
    }
    inv.setItem(52, button(Material.ARROW, "Back"));
    inv.setItem(53, button(Material.LIME_WOOL, "Next"));
    return inv;
  }

  private Inventory addWeightsInv(AddContext ctx) {
    Map<Integer, Integer> idx = new HashMap<>();
    Inventory inv = Bukkit.createInventory(new Holder(Type.ADD_WEIGHTS, null, new HashMap<>(), new HashMap<>(), idx, ctx), 54,
        "Set Weights");
    int slot = 0;
    for (int i = 0; i < ctx.items.size() && slot < 52; i++) {
      ItemStack item = ctx.items.get(i).clone();
      double w;
      if (ctx.weights.size() > i) {
        w = ctx.weights.get(i);
      } else {
        w = 1.0;
        ctx.weights.add(w);
      }
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Weight: " + String.format("%.2f", w)));
        lore.add(Component.text("L +1, R -1"));
        meta.lore(lore);
        item.setItemMeta(meta);
      }
      inv.setItem(slot, item);
      idx.put(slot, i);
      slot++;
    }
    inv.setItem(52, button(Material.ARROW, "Back"));
    inv.setItem(53, button(Material.LIME_WOOL, "Save"));
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
        lore.add(Component.text("Shift to edit"));
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

  private Inventory catWeightsInv() {
    Map<Integer, Category> map = new HashMap<>();
    Inventory inv = Bukkit.createInventory(new Holder(Type.CAT_WEIGHTS, new HashMap<>(), map), 54,
        "Category Weights");
    int slot = 0;
    for (Category cat : Category.values()) {
      ItemStack item = new ItemStack(Material.PAPER);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.text(cat.name()));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text(
            "Weight: " + String.format("%.2f", lootService.getBaseCategoryWeight(cat))));
        lore.add(Component.text("Left +1, Right -1"));
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

  private Inventory entryInv(LootEntry e) {
    Inventory inv = Bukkit.createInventory(new Holder(Type.ENTRY, e), 27, "Edit " + e.key());

    ItemStack cat = new ItemStack(Material.PAPER);
    ItemMeta cMeta = cat.getItemMeta();
    if (cMeta != null) {
      cMeta.displayName(Component.text("Category: " + e.category().name()));
      cMeta.lore(java.util.List.of(Component.text("Click to cycle")));
      cat.setItemMeta(cMeta);
    }
    inv.setItem(10, cat);

    ItemStack rod = new ItemStack(Material.FISHING_ROD);
    ItemMeta rMeta = rod.getItemMeta();
    if (rMeta != null) {
      rMeta.displayName(Component.text("Min Rod Level"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(String.valueOf(e.minRodLevel())));
      lore.add(Component.text("L +1, R -1"));
      rMeta.lore(lore);
      rod.setItemMeta(rMeta);
    }
    inv.setItem(11, rod);

    ItemStack bc = new ItemStack(e.broadcast() ? Material.LIME_DYE : Material.GRAY_DYE);
    ItemMeta bMeta = bc.getItemMeta();
    if (bMeta != null) {
      bMeta.displayName(Component.text("Broadcast: " + (e.broadcast() ? "Yes" : "No")));
      bMeta.lore(java.util.List.of(Component.text("Click to toggle")));
      bc.setItemMeta(bMeta);
    }
    inv.setItem(12, bc);

    ItemStack priceBase = new ItemStack(Material.GOLD_INGOT);
    ItemMeta pbMeta = priceBase.getItemMeta();
    if (pbMeta != null) {
      pbMeta.displayName(Component.text("Price Base"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(String.format("%.2f", e.priceBase())));
      lore.add(Component.text("Click to edit"));
      pbMeta.lore(lore);
      priceBase.setItemMeta(pbMeta);
    }
    inv.setItem(13, priceBase);

    ItemStack priceKg = new ItemStack(Material.GOLD_NUGGET);
    ItemMeta pkMeta = priceKg.getItemMeta();
    if (pkMeta != null) {
      pkMeta.displayName(Component.text("Price Per Kg"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(String.format("%.2f", e.pricePerKg())));
      lore.add(Component.text("Click to edit"));
      pkMeta.lore(lore);
      priceKg.setItemMeta(pkMeta);
    }
    inv.setItem(14, priceKg);

    ItemStack payout = new ItemStack(Material.EMERALD);
    ItemMeta payMeta = payout.getItemMeta();
    if (payMeta != null) {
      payMeta.displayName(Component.text("Payout Multiplier"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(String.format("%.2f", e.payoutMultiplier())));
      lore.add(Component.text("Click to edit"));
      payMeta.lore(lore);
      payout.setItemMeta(payMeta);
    }
    inv.setItem(15, payout);

    ItemStack minW = new ItemStack(Material.FEATHER);
    ItemMeta minMeta = minW.getItemMeta();
    if (minMeta != null) {
      minMeta.displayName(Component.text("Min Weight g"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(String.format("%.2f", e.minWeightG())));
      lore.add(Component.text("Click to edit"));
      minMeta.lore(lore);
      minW.setItemMeta(minMeta);
    }
    inv.setItem(16, minW);

    ItemStack maxW = new ItemStack(Material.PRISMARINE_CRYSTALS);
    ItemMeta maxMeta = maxW.getItemMeta();
    if (maxMeta != null) {
      maxMeta.displayName(Component.text("Max Weight g"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(String.format("%.2f", e.maxWeightG())));
      lore.add(Component.text("Click to edit"));
      maxMeta.lore(lore);
      maxW.setItemMeta(maxMeta);
    }
    inv.setItem(17, maxW);

    inv.setItem(26, button(Material.BARRIER, "Back"));
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
    Inventory inv =
        Bukkit.createInventory(new Holder(Type.SCALING, new HashMap<>(), map), 54, "Scaling");

    Map<Integer, Map<Category, Double>> levelWeights = new HashMap<>();
    Map<Integer, Double> levelTotals = new HashMap<>();
    for (int lvl : PREVIEW_LEVELS) {
      Map<Category, Double> weights = new EnumMap<>(Category.class);
      double total = 0.0;
      for (LootEntry e : lootService.getEntries()) {
        double w = lootService.effectiveWeight(e, lvl);
        weights.merge(e.category(), w, Double::sum);
        total += w;
      }
      levelWeights.put(lvl, weights);
      levelTotals.put(lvl, total);
    }

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
        for (int lvl : PREVIEW_LEVELS) {
          double w = levelWeights.get(lvl).getOrDefault(cat, 0.0);
          double total = levelTotals.get(lvl);
          double pct = total > 0 ? (w / total) * 100.0 : 0.0;
          lore.add(Component.text(String.format("L%d: %.2f (%.1f%%)", lvl, w, pct)));
        }
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

  private Inventory mirrorInv(Player player) {
    MirrorData data = mirrorEditors.computeIfAbsent(player.getUniqueId(), k -> new MirrorData());
    Inventory inv = Bukkit.createInventory(new Holder(Type.MIRROR_ADD), 27, "Mirror Item");

    ItemStack cat = new ItemStack(Material.PAPER);
    ItemMeta cMeta = cat.getItemMeta();
    if (cMeta != null) {
      cMeta.displayName(Component.text("Category: " + data.category.name()));
      cMeta.lore(java.util.List.of(Component.text("Click to cycle")));
      cat.setItemMeta(cMeta);
    }
    inv.setItem(10, cat);

    ItemStack bc = new ItemStack(data.broadcast ? Material.LIME_DYE : Material.GRAY_DYE);
    ItemMeta bMeta = bc.getItemMeta();
    if (bMeta != null) {
      bMeta.displayName(Component.text("Broadcast: " + (data.broadcast ? "Yes" : "No")));
      bMeta.lore(java.util.List.of(Component.text("Click to toggle")));
      bc.setItemMeta(bMeta);
    }
    inv.setItem(12, bc);

    ItemStack key = new ItemStack(Material.NAME_TAG);
    ItemMeta kMeta = key.getItemMeta();
    if (kMeta != null) {
      kMeta.displayName(Component.text("Loot Key"));
      java.util.List<Component> lore = new java.util.ArrayList<>();
      lore.add(Component.text(data.key == null ? "None" : data.key));
      lore.add(Component.text("Click to select"));
      kMeta.lore(lore);
      key.setItemMeta(kMeta);
    }
    inv.setItem(14, key);

    inv.setItem(16, button(Material.GREEN_WOOL, "Save"));
    inv.setItem(26, button(Material.BARRIER, "Back"));
    return inv;
  }

  private Inventory mirrorSelectInv() {
    Map<Integer, LootEntry> map = new HashMap<>();
    Inventory inv = Bukkit.createInventory(new Holder(Type.MIRROR_SELECT, map), 54, "Select Loot");
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

  private void openCatWeights(Player player) {
    player.openInventory(catWeightsInv());
  }

  private void openEconomy(Player player) {
    player.openInventory(economyInv());
  }

  private void openMirrorAdd(Player player) {
    player.openInventory(mirrorInv(player));
  }

  private void openMirrorSelect(Player player) {
    player.openInventory(mirrorSelectInv());
  }

  private void openEntry(Player player, LootEntry entry) {
    player.openInventory(entryInv(entry));
  }

  private void openAddItems(Player player, AddContext ctx) {
    player.openInventory(addItemsInv(ctx));
  }

  private void openAddCats(Player player, AddContext ctx) {
    player.openInventory(addCatsInv(ctx));
  }

  private void openAddWeights(Player player, AddContext ctx) {
    player.openInventory(addWeightsInv(ctx));
  }

  private void saveNewDrops(Player player, AddContext ctx) {
    for (int i = 0; i < ctx.items.size(); i++) {
      ItemStack item = ctx.items.get(i);
      Category cat = ctx.categories.size() > i ? ctx.categories.get(i) : Category.FISH;
      double w = ctx.weights.size() > i ? ctx.weights.get(i) : 1.0;
      String key = cat.name().toLowerCase() + "_" + UUID.randomUUID();
      LootEntry entry = new LootEntry(key, cat, w, 0, false, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0,
          1.0, 0.0, 0.0, ItemSerialization.toBase64(item));
      lootService.addEntry(entry);
      try {
        lootRepo.upsert(entry);
      } catch (SQLException e) {
        player.sendMessage("DB error: " + e.getMessage());
      }
    }
    player.sendMessage("Added " + ctx.items.size() + " drops.");
  }

  private void saveMirror(Player player) {
    MirrorData data = mirrorEditors.get(player.getUniqueId());
    if (data == null || data.key == null) {
      player.sendMessage("Select loot entry first.");
      return;
    }
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      player.sendMessage("Hold an item in your hand.");
      return;
    }
    MirrorItem mi = new MirrorItem(data.key, data.category, data.broadcast,
        ItemSerialization.toBase64(item));
    mirrorItemService.add(mi);
    try {
      mirrorItemRepo.upsert(mi);
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
    }
    player.sendMessage("Added mirror item for " + data.key);
  }

  private void adjustWeight(Player player, LootEntry e, double delta) {
    double newWeight = Math.max(0.0, e.baseWeight() + delta);
    LootEntry updated = new LootEntry(e.key(), e.category(), newWeight, e.minRodLevel(), e.broadcast(),
        e.priceBase(), e.pricePerKg(), e.payoutMultiplier(), e.qualitySWeight(), e.qualityAWeight(),
        e.qualityBWeight(), e.qualityCWeight(), e.minWeightG(), e.maxWeightG(), e.itemBase64());
    saveEntry(player, updated);
  }

  private void saveEntry(Player player, LootEntry entry) {
    lootService.updateEntry(entry);
    try {
      lootRepo.upsert(entry);
    } catch (SQLException ex) {
      player.sendMessage("DB error: " + ex.getMessage());
    }
  }

  private void adjustCategoryWeight(Player player, Category cat, double delta) {
    double newWeight = Math.max(0.0, lootService.getBaseCategoryWeight(cat) + delta);
    lootService.setBaseCategoryWeight(cat, newWeight);
    try {
      paramRepo.set("category_weight_" + cat.name(), String.valueOf(newWeight));
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
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
          AddContext ctx = new AddContext();
          addEditors.put(player.getUniqueId(), ctx);
          openAddItems(player, ctx);
        } else if (slot == 12) {
          openWeights(player);
        } else if (slot == 14) {
          openScaling(player);
        } else if (slot == 16) {
          questMenu.open(player);
        } else if (slot == 18) {
          openMirrorAdd(player);
        } else if (slot == 20) {
          openEconomy(player);
        } else if (slot == 22) {
          openCatWeights(player);
        }
      }
      case ADD_ITEMS -> {
        int raw = event.getRawSlot();
        if (raw == 52) {
          open(player);
          return;
        }
        if (raw == 53) {
          AddContext ctx = addEditors.get(player.getUniqueId());
          if (ctx != null) {
            ctx.items.clear();
            Inventory top = event.getInventory();
            for (int i = 0; i < 52; i++) {
              ItemStack it = top.getItem(i);
              if (it != null && !it.getType().isAir()) {
                ctx.items.add(it.clone());
              }
            }
            ctx.categories.clear();
            ctx.weights.clear();
            openAddCats(player, ctx);
          }
          return;
        }
        if (raw < 52 || raw >= event.getView().getTopInventory().getSize()) {
          event.setCancelled(false);
        }
      }
      case ADD_CATS -> {
        int raw = event.getRawSlot();
        AddContext ctx = holder.addCtx;
        if (raw == 52) {
          openAddItems(player, ctx);
          return;
        }
        if (raw == 53) {
          openAddWeights(player, ctx);
          return;
        }
        Integer idx = holder.indexMap.get(raw);
        if (idx != null) {
          Category current = ctx.categories.get(idx);
          Category[] vals = Category.values();
          int next = (current.ordinal() + 1) % vals.length;
          ctx.categories.set(idx, vals[next]);
          openAddCats(player, ctx);
        }
      }
      case ADD_WEIGHTS -> {
        int raw = event.getRawSlot();
        AddContext ctx = holder.addCtx;
        if (raw == 52) {
          openAddCats(player, ctx);
          return;
        }
        if (raw == 53) {
          saveNewDrops(player, ctx);
          addEditors.remove(player.getUniqueId());
          open(player);
          return;
        }
        Integer idx = holder.indexMap.get(raw);
        if (idx != null) {
          double delta = event.getClick() == ClickType.RIGHT ? -1.0 : 1.0;
          double val = Math.max(0.0, ctx.weights.get(idx) + delta);
          ctx.weights.set(idx, val);
          openAddWeights(player, ctx);
        }
      }
      case WEIGHTS -> {
        if (event.getRawSlot() == 53) {
          open(player);
          return;
        }
        LootEntry e = holder.weightMap.get(event.getRawSlot());
        if (e != null) {
          ClickType ct = event.getClick();
          if (ct == ClickType.SHIFT_LEFT || ct == ClickType.SHIFT_RIGHT) {
            openEntry(player, e);
          } else {
            double delta = ct == ClickType.RIGHT ? -1.0 : 1.0;
            adjustWeight(player, e, delta);
            openWeights(player);
          }
        }
      }
      case CAT_WEIGHTS -> {
        if (event.getRawSlot() == 53) {
          open(player);
          return;
        }
        Category cat = holder.scaleMap.get(event.getRawSlot());
        if (cat != null) {
          double delta = event.getClick() == ClickType.RIGHT ? -1.0 : 1.0;
          adjustCategoryWeight(player, cat, delta);
          openCatWeights(player);
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
          editors.put(player.getUniqueId(), new Editor(msg -> {
            quickSellService.setCurrencySymbol(msg);
            try {
              paramRepo.set("currency_symbol", msg);
            } catch (SQLException e) {
              player.sendMessage("DB error: " + e.getMessage());
            }
          }, p -> openEconomy(p)));
          player.closeInventory();
          player.sendMessage("Enter currency symbol in chat");
        }
      }
      case MIRROR_ADD -> {
        int slot = event.getRawSlot();
        MirrorData data = mirrorEditors.computeIfAbsent(player.getUniqueId(), k -> new MirrorData());
        if (slot == 26) {
          open(player);
          return;
        }
        if (slot == 10) {
          Category[] cats = Category.values();
          data.category = cats[(data.category.ordinal() + 1) % cats.length];
          openMirrorAdd(player);
        } else if (slot == 12) {
          data.broadcast = !data.broadcast;
          openMirrorAdd(player);
        } else if (slot == 14) {
          openMirrorSelect(player);
        } else if (slot == 16) {
          saveMirror(player);
          open(player);
        }
      }
      case MIRROR_SELECT -> {
        if (event.getRawSlot() == 53) {
          openMirrorAdd(player);
          return;
        }
        LootEntry e = holder.weightMap.get(event.getRawSlot());
        if (e != null) {
          MirrorData data = mirrorEditors.computeIfAbsent(player.getUniqueId(), k -> new MirrorData());
          data.key = e.key();
          openMirrorAdd(player);
        }
      }
      case ENTRY -> {
        int slot = event.getRawSlot();
        LootEntry e = holder.entry;
        if (slot == 26) {
          openWeights(player);
          return;
        }
        if (e == null) {
          openWeights(player);
          return;
        }
        if (slot == 10) {
          Category[] cats = Category.values();
          Category newCat = cats[(e.category().ordinal() + 1) % cats.length];
          LootEntry updated = new LootEntry(e.key(), newCat, e.baseWeight(), e.minRodLevel(),
              e.broadcast(), e.priceBase(), e.pricePerKg(), e.payoutMultiplier(), e.qualitySWeight(),
              e.qualityAWeight(), e.qualityBWeight(), e.qualityCWeight(), e.minWeightG(),
              e.maxWeightG(), e.itemBase64());
          saveEntry(player, updated);
          openEntry(player, updated);
        } else if (slot == 11) {
          int delta = event.getClick() == ClickType.RIGHT ? -1 : 1;
          int newLevel = Math.max(0, e.minRodLevel() + delta);
          LootEntry updated = new LootEntry(e.key(), e.category(), e.baseWeight(), newLevel,
              e.broadcast(), e.priceBase(), e.pricePerKg(), e.payoutMultiplier(), e.qualitySWeight(),
              e.qualityAWeight(), e.qualityBWeight(), e.qualityCWeight(), e.minWeightG(),
              e.maxWeightG(), e.itemBase64());
          saveEntry(player, updated);
          openEntry(player, updated);
        } else if (slot == 12) {
          LootEntry updated = new LootEntry(e.key(), e.category(), e.baseWeight(), e.minRodLevel(),
              !e.broadcast(), e.priceBase(), e.pricePerKg(), e.payoutMultiplier(), e.qualitySWeight(),
              e.qualityAWeight(), e.qualityBWeight(), e.qualityCWeight(), e.minWeightG(),
              e.maxWeightG(), e.itemBase64());
          saveEntry(player, updated);
          openEntry(player, updated);
        } else if (slot == 13) {
          editors.put(player.getUniqueId(), new Editor(msg -> {
            try {
              double val = Double.parseDouble(msg);
              LootEntry updated = new LootEntry(e.key(), e.category(), e.baseWeight(),
                  e.minRodLevel(), e.broadcast(), val, e.pricePerKg(), e.payoutMultiplier(),
                  e.qualitySWeight(), e.qualityAWeight(), e.qualityBWeight(), e.qualityCWeight(),
                  e.minWeightG(), e.maxWeightG(), e.itemBase64());
              saveEntry(player, updated);
            } catch (NumberFormatException ex) {
              player.sendMessage("Invalid number");
            }
          }, p -> openEntry(p, lootService.getEntry(e.key()))));
          player.closeInventory();
          player.sendMessage("Enter price base in chat");
        } else if (slot == 14) {
          editors.put(player.getUniqueId(), new Editor(msg -> {
            try {
              double val = Double.parseDouble(msg);
              LootEntry updated = new LootEntry(e.key(), e.category(), e.baseWeight(),
                  e.minRodLevel(), e.broadcast(), e.priceBase(), val, e.payoutMultiplier(),
                  e.qualitySWeight(), e.qualityAWeight(), e.qualityBWeight(), e.qualityCWeight(),
                  e.minWeightG(), e.maxWeightG(), e.itemBase64());
              saveEntry(player, updated);
            } catch (NumberFormatException ex) {
              player.sendMessage("Invalid number");
            }
          }, p -> openEntry(p, lootService.getEntry(e.key()))));
          player.closeInventory();
          player.sendMessage("Enter price per kg in chat");
        } else if (slot == 15) {
          editors.put(player.getUniqueId(), new Editor(msg -> {
            try {
              double val = Double.parseDouble(msg);
              LootEntry updated = new LootEntry(e.key(), e.category(), e.baseWeight(),
                  e.minRodLevel(), e.broadcast(), e.priceBase(), e.pricePerKg(), val,
                  e.qualitySWeight(), e.qualityAWeight(), e.qualityBWeight(), e.qualityCWeight(),
                  e.minWeightG(), e.maxWeightG(), e.itemBase64());
              saveEntry(player, updated);
            } catch (NumberFormatException ex) {
              player.sendMessage("Invalid number");
            }
          }, p -> openEntry(p, lootService.getEntry(e.key()))));
          player.closeInventory();
          player.sendMessage("Enter payout multiplier in chat");
        } else if (slot == 16) {
          editors.put(player.getUniqueId(), new Editor(msg -> {
            try {
              double val = Double.parseDouble(msg);
              LootEntry updated = new LootEntry(e.key(), e.category(), e.baseWeight(),
                  e.minRodLevel(), e.broadcast(), e.priceBase(), e.pricePerKg(),
                  e.payoutMultiplier(), e.qualitySWeight(), e.qualityAWeight(), e.qualityBWeight(),
                  e.qualityCWeight(), val, e.maxWeightG(), e.itemBase64());
              saveEntry(player, updated);
            } catch (NumberFormatException ex) {
              player.sendMessage("Invalid number");
            }
          }, p -> openEntry(p, lootService.getEntry(e.key()))));
          player.closeInventory();
          player.sendMessage("Enter min weight g in chat");
        } else if (slot == 17) {
          editors.put(player.getUniqueId(), new Editor(msg -> {
            try {
              double val = Double.parseDouble(msg);
              LootEntry updated = new LootEntry(e.key(), e.category(), e.baseWeight(),
                  e.minRodLevel(), e.broadcast(), e.priceBase(), e.pricePerKg(),
                  e.payoutMultiplier(), e.qualitySWeight(), e.qualityAWeight(), e.qualityBWeight(),
                  e.qualityCWeight(), e.minWeightG(), val, e.itemBase64());
              saveEntry(player, updated);
            } catch (NumberFormatException ex) {
              player.sendMessage("Invalid number");
            }
          }, p -> openEntry(p, lootService.getEntry(e.key()))));
          player.closeInventory();
          player.sendMessage("Enter max weight g in chat");
        }
      }
    }
  }

  @EventHandler
  public void onChat(AsyncPlayerChatEvent event) {
    Editor editor = editors.remove(event.getPlayer().getUniqueId());
    if (editor == null) {
      return;
    }
    event.setCancelled(true);
    String msg = event.getMessage();
    Bukkit.getScheduler().runTask(plugin, () -> {
      editor.consumer.accept(msg);
      event.getPlayer().sendMessage("Updated.");
      editor.after.accept(event.getPlayer());
    });
  }

  private static class Editor {
    final Consumer<String> consumer;
    final Consumer<Player> after;
    Editor(Consumer<String> consumer, Consumer<Player> after) {
      this.consumer = consumer;
      this.after = after;
    }
  }

  private static class AddContext {
    final java.util.List<ItemStack> items = new java.util.ArrayList<>();
    final java.util.List<Category> categories = new java.util.ArrayList<>();
    final java.util.List<Double> weights = new java.util.ArrayList<>();
  }

  private static class MirrorData {
    Category category = Category.FISH;
    boolean broadcast = false;
    String key;
  }

  private static class Holder implements InventoryHolder {
    final Type type;
    final Map<Integer, LootEntry> weightMap;
    final Map<Integer, Category> scaleMap;
    final Map<Integer, Integer> indexMap;
    final LootEntry entry;
    final AddContext addCtx;
    Holder(Type type) {
      this(type, null, new HashMap<>(), new HashMap<>(), new HashMap<>(), null);
    }
    Holder(Type type, Map<Integer, LootEntry> weightMap) {
      this(type, null, weightMap, new HashMap<>(), new HashMap<>(), null);
    }
    Holder(Type type, Map<Integer, LootEntry> weightMap, Map<Integer, Category> scaleMap) {
      this(type, null, weightMap, scaleMap, new HashMap<>(), null);
    }
    Holder(Type type, AddContext ctx) {
      this(type, null, new HashMap<>(), new HashMap<>(), new HashMap<>(), ctx);
    }
    Holder(Type type, LootEntry entry) {
      this(type, entry, new HashMap<>(), new HashMap<>(), new HashMap<>(), null);
    }
    Holder(Type type, LootEntry entry, Map<Integer, LootEntry> weightMap,
        Map<Integer, Category> scaleMap) {
      this(type, entry, weightMap, scaleMap, new HashMap<>(), null);
    }
    Holder(Type type, LootEntry entry, Map<Integer, LootEntry> weightMap,
        Map<Integer, Category> scaleMap, Map<Integer, Integer> indexMap, AddContext ctx) {
      this.type = type;
      this.entry = entry;
      this.weightMap = weightMap;
      this.scaleMap = scaleMap;
      this.indexMap = indexMap;
      this.addCtx = ctx;
    }
    @Override
    public Inventory getInventory() {
      return null;
    }
  }
}

