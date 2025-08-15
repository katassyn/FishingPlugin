package org.maks.fishingPlugin;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.fishingPlugin.command.FishingCommand;
import org.maks.fishingPlugin.data.Database;
import org.maks.fishingPlugin.data.LootRepo;
import org.maks.fishingPlugin.data.MirrorItemRepo;
import org.maks.fishingPlugin.data.ParamRepo;
import org.maks.fishingPlugin.data.ProfileRepo;
import org.maks.fishingPlugin.data.QuestRepo;
import org.maks.fishingPlugin.data.QuestProgressRepo;
import org.maks.fishingPlugin.listener.FishingListener;
import org.maks.fishingPlugin.listener.ProfileListener;
import org.maks.fishingPlugin.listener.QteListener;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.model.MirrorItem;
import org.maks.fishingPlugin.model.ScaleConf;
import org.maks.fishingPlugin.model.ScaleMode;
import org.maks.fishingPlugin.service.AntiCheatService;
import org.maks.fishingPlugin.service.Awarder;
import org.maks.fishingPlugin.service.LevelService;
import org.maks.fishingPlugin.service.LootService;
import org.maks.fishingPlugin.service.QteService;
import org.maks.fishingPlugin.service.QuestChainService;
import org.maks.fishingPlugin.service.QuickSellService;
import org.maks.fishingPlugin.service.MirrorItemService;
import org.maks.fishingPlugin.service.TeleportService;
import org.maks.fishingPlugin.service.RodService;
import org.maks.fishingPlugin.util.ItemSerialization;
import org.maks.fishingPlugin.gui.MainMenu;
import org.maks.fishingPlugin.gui.QuickSellMenu;
import org.maks.fishingPlugin.gui.ShopMenu;
import org.maks.fishingPlugin.gui.QuestMenu;
import org.maks.fishingPlugin.gui.AdminLootEditorMenu;
import org.maks.fishingPlugin.gui.AdminQuestEditorMenu;
import org.maks.fishingPlugin.command.QuickSellCommand;
import org.maks.fishingPlugin.command.GiveRodCommand;
import net.milkbowl.vault.economy.Economy;

public final class FishingPlugin extends JavaPlugin {

    private LevelService levelService;
    private LootService lootService;
    private Awarder awarder;
    private QuickSellService quickSellService;
    private QteService qteService;
    private AntiCheatService antiCheatService;
    private QuestChainService questService;
    private TeleportService teleportService;
    private RodService rodService;
    private Economy economy;
    private int requiredPlayerLevel;
    private Database database;
    private LootRepo lootRepo;
    private MirrorItemRepo mirrorItemRepo;
    private QuestRepo questRepo;
    private QuestProgressRepo questProgressRepo;
    private ParamRepo paramRepo;
    private ProfileRepo profileRepo;
    private MirrorItemService mirrorItemService;
    private boolean hasEliteLootbox;
    private boolean hasWorldGuard;
    private boolean hasCitizens;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var pm = getServer().getPluginManager();
        this.hasEliteLootbox = pm.getPlugin("EliteLootbox") != null;
        this.hasWorldGuard = pm.getPlugin("WorldGuard") != null;
        this.hasCitizens = pm.getPlugin("Citizens") != null;

        var dbSec = getConfig().getConfigurationSection("database");
        String host = dbSec != null ? dbSec.getString("host", "localhost") : "localhost";
        int port = dbSec != null ? dbSec.getInt("port", 3306) : 3306;
        String dbName = dbSec != null ? dbSec.getString("name", "fishing") : "fishing";
        String user = dbSec != null ? dbSec.getString("user", "root") : "root";
        String pass = dbSec != null ? dbSec.getString("password", "") : "";
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s", host, port, dbName);
        try {
            this.database = new Database(jdbcUrl, user, pass);
        } catch (SQLException e) {
            getLogger().severe("Failed to connect to database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        DataSource ds = database.getDataSource();
        this.lootRepo = new LootRepo(ds);
        this.mirrorItemRepo = new MirrorItemRepo(ds);
        this.questRepo = new QuestRepo(ds);
        this.questProgressRepo = new QuestProgressRepo(ds);
        this.paramRepo = new ParamRepo(ds);
        this.profileRepo = new ProfileRepo(ds);
        try {
            lootRepo.init();
            mirrorItemRepo.init();
            questRepo.init();
            questProgressRepo.init();
            paramRepo.init();
            profileRepo.init();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database tables: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            seedDefaultLoot();
        } catch (SQLException e) {
            getLogger().warning("Failed to seed default loot: " + e.getMessage());
        }
        var lvlSec = getConfig().getConfigurationSection("leveling");
        double expBase = 50;
        double expCoeff = 0.09;
        double expPower = 2.3;
        if (lvlSec != null) {
            expBase = lvlSec.getDouble("exp_base", expBase);
            expCoeff = lvlSec.getDouble("exp_coeff", expCoeff);
            expPower = lvlSec.getDouble("exp_power", expPower);
        }
        var gainSec = getConfig().getConfigurationSection("exp_gain");
        double fishBase = 9;
        double fishPerKg = 0.5;
        double chestBase = 22;
        double runeBase = 40;
        double treasureBase = 80;
        if (gainSec != null) {
            fishBase = gainSec.getDouble("fish_base", fishBase);
            fishPerKg = gainSec.getDouble("fish_per_kg", fishPerKg);
            chestBase = gainSec.getDouble("chest_base", chestBase);
            runeBase = gainSec.getDouble("rune_base", runeBase);
            treasureBase = gainSec.getDouble("treasure_base", treasureBase);
        }

        this.levelService = new LevelService(profileRepo, this,
            expBase, expCoeff, expPower,
            fishBase, fishPerKg, chestBase, runeBase, treasureBase);
        this.mirrorItemService = new MirrorItemService();
        this.rodService = new RodService(this, levelService);
        this.levelService.setRodService(rodService);

        Map<String, String> params = new HashMap<>();
        try {
            params = paramRepo.findAll();
        } catch (SQLException e) {
            getLogger().warning("Failed to load params: " + e.getMessage());
        }

        this.requiredPlayerLevel = Integer.parseInt(params.getOrDefault(
            "player_level_requirement",
            String.valueOf(getConfig().getInt("player_level_requirement", 80))));

        Map<Category, ScaleConf> scaling = new EnumMap<>(Category.class);
        var scaleSec = getConfig().getConfigurationSection("category_scaling");
        if (scaleSec != null) {
            for (String catKey : scaleSec.getKeys(false)) {
                try {
                    Category cat = Category.valueOf(catKey.toUpperCase());
                    var conf = scaleSec.getConfigurationSection(catKey);
                    if (conf != null) {
                        ScaleMode mode = ScaleMode.valueOf(conf.getString("mode", "EXP").toUpperCase());
                        double param = conf.getDouble(mode == ScaleMode.EXP ? "beta" : "alpha");
                        double k = conf.getDouble("k", 0);
                        scaling.put(cat, new ScaleConf(mode, param, k));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (Category cat : Category.values()) {
            String mKey = "scale_mode_" + cat.name();
            if (params.containsKey(mKey)) {
                try {
                    ScaleMode mode = ScaleMode.valueOf(params.get(mKey));
                    double a = Double.parseDouble(params.getOrDefault("scale_a_" + cat.name(), "0"));
                    double k = Double.parseDouble(params.getOrDefault("scale_k_" + cat.name(), "0"));
                    scaling.put(cat, new ScaleConf(mode, a, k));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        Map<Category, Double> catWeights = new EnumMap<>(Category.class);
        var weightSec = getConfig().getConfigurationSection("category_weights");
        if (weightSec != null) {
            for (String catKey : weightSec.getKeys(false)) {
                try {
                    Category cat = Category.valueOf(catKey.toUpperCase());
                    catWeights.put(cat, weightSec.getDouble(catKey));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (Category cat : Category.values()) {
            double w = Double.parseDouble(params.getOrDefault("category_weight_" + cat.name(),
                String.valueOf(catWeights.getOrDefault(cat, 1.0))));
            catWeights.put(cat, w);
        }
        this.lootService = new LootService(scaling, catWeights);
        try {
            for (LootEntry entry : lootRepo.findAll()) {
                lootService.addEntry(entry);
            }
        } catch (SQLException e) {
            getLogger().warning("Failed to load loot entries: " + e.getMessage());
        }
        try {
            for (MirrorItem mi : mirrorItemRepo.findAll()) {
                mirrorItemService.add(mi);
            }
        } catch (SQLException e) {
            getLogger().warning("Failed to load mirror items: " + e.getMessage());
        }
        this.economy = Bukkit.getServicesManager().getRegistration(Economy.class).getProvider();
        this.awarder = new Awarder(this, mirrorItemService);
        var econSec = getConfig().getConfigurationSection("economy");
        double multiplier = 1.0;
        double tax = 0.0;
        String symbol = "$";
        if (econSec != null) {
            multiplier = econSec.getDouble("global_multiplier", 1.0);
            tax = econSec.getDouble("quicksell_tax", 0.0);
            symbol = econSec.getString("currency_symbol", "$");
        }
        multiplier = Double.parseDouble(params.getOrDefault("global_multiplier", String.valueOf(multiplier)));
        tax = Double.parseDouble(params.getOrDefault("quicksell_tax", String.valueOf(tax)));
        symbol = params.getOrDefault("currency_symbol", symbol);
        this.quickSellService = new QuickSellService(this, lootService, economy, levelService, multiplier, tax, symbol);

        var acSec = getConfig().getConfigurationSection("anti_cheat");
        int sampleSize = acSec != null ? acSec.getInt("sample_size", 5) : 5;
        long toleranceMs = acSec != null ? acSec.getLong("tolerance_ms", 30) : 30;
        double dropMult = acSec != null ? acSec.getDouble("drop_multiplier", 0.5) : 0.5;

        this.antiCheatService = new AntiCheatService(sampleSize, toleranceMs);

        var qteSec = getConfig().getConfigurationSection("qte");
        double chance = qteSec != null ? qteSec.getDouble("chance", 0.25) : 0.25;
        long duration = qteSec != null ? qteSec.getLong("duration_ms", 1000L) : 1000L;

        this.qteService = new QteService(chance, duration);
        this.teleportService = new TeleportService(this);
        this.questService = new QuestChainService(economy, questRepo, questProgressRepo, this);
        if (pm.getPlugin("PlaceholderAPI") != null) {
            new org.maks.fishingPlugin.integration.FishingExpansion(this).register();
        }

        Bukkit.getPluginManager().registerEvents(new ProfileListener(levelService, antiCheatService, rodService), this);
        Bukkit.getPluginManager().registerEvents(
            new FishingListener(lootService, awarder, levelService, qteService, questService, requiredPlayerLevel,
                antiCheatService, dropMult), this);
        Bukkit.getPluginManager().registerEvents(new QteListener(qteService), this);
        Bukkit.getOnlinePlayers().forEach(p -> {
            levelService.loadProfile(p);
            antiCheatService.deserialize(p.getUniqueId(), levelService.getLastQteSample(p));
        });

        QuickSellMenu quickSellMenu = new QuickSellMenu(quickSellService);
        ShopMenu shopMenu = new ShopMenu(this, requiredPlayerLevel);
        QuestMenu questMenu = new QuestMenu(questService);
        AdminQuestEditorMenu adminQuestMenu = new AdminQuestEditorMenu(this, questService, questRepo);
        AdminLootEditorMenu adminMenu = new AdminLootEditorMenu(this, lootService, lootRepo, paramRepo,
            quickSellService, adminQuestMenu, mirrorItemRepo, mirrorItemService);
        MainMenu mainMenu = new MainMenu(shopMenu, teleportService, requiredPlayerLevel);
        getCommand("fishing").setExecutor(new FishingCommand(mainMenu, adminMenu, requiredPlayerLevel));
        getCommand("fishsell").setExecutor(new QuickSellCommand(quickSellMenu));
        getCommand("fishingrod").setExecutor(new GiveRodCommand(rodService));

        Bukkit.getPluginManager().registerEvents(mainMenu, this);
        Bukkit.getPluginManager().registerEvents(quickSellMenu, this);
        Bukkit.getPluginManager().registerEvents(questMenu, this);
        Bukkit.getPluginManager().registerEvents(adminMenu, this);
        Bukkit.getPluginManager().registerEvents(adminQuestMenu, this);

        getLogger().info("FishingPlugin enabled");
    }

    public LevelService getLevelService() {
        return levelService;
    }

    public LootService getLootService() {
        return lootService;
    }

    public QuestChainService getQuestService() {
        return questService;
    }

    public boolean hasEliteLootbox() {
        return hasEliteLootbox;
    }

    public boolean hasWorldGuard() {
        return hasWorldGuard;
    }

    public boolean hasCitizens() {
        return hasCitizens;
    }

    @Override
    public void onDisable() {
        if (levelService != null) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                levelService.setLastQteSample(p, antiCheatService.serialize(p.getUniqueId()));
                levelService.saveProfile(p);
                antiCheatService.reset(p.getUniqueId());
            });
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("FishingPlugin disabled");
    }

    private void seedDefaultLoot() throws SQLException {
        if (!lootRepo.findAll().isEmpty()) {
            return;
        }
        LootEntry[] defaults = new LootEntry[] {
            fish("Chinook Salmon", Material.SALMON, 60, 1000, 7000, 7, 3),
            fish("Coho Salmon", Material.SALMON, 60, 1000, 6000, 7, 3),
            fish("Sockeye Salmon", Material.SALMON, 60, 800, 5000, 7, 3),
            fish("Pink Salmon", Material.SALMON, 60, 700, 4500, 7, 3),
            fish("Atlantic Salmon", Material.SALMON, 60, 1200, 8000, 7, 3),
            fish("Clownfish", Material.TROPICAL_FISH, 30, 200, 500, 6, 2),
            fish("Parrotfish", Material.TROPICAL_FISH, 30, 200, 600, 6, 2),
            fish("Butterflyfish", Material.TROPICAL_FISH, 30, 150, 450, 6, 2),
            fish("Blue Tang", Material.TROPICAL_FISH, 30, 150, 550, 6, 2),
            fish("Lionfish", Material.TROPICAL_FISH, 30, 180, 520, 6, 2),
            fish("Angelfish", Material.TROPICAL_FISH, 30, 170, 530, 6, 2),
            fish("Pufferfish", Material.PUFFERFISH, 20, 600, 2000, 12, 6),
            craft("alga_I", "&9[ I ] Algal", ChatColor.GRAY + "" + ChatColor.ITALIC + "Basic crafting material", Material.HORN_CORAL, 40),
            craft("alga_II", "&5[ II ] Algal", ChatColor.GRAY + "" + ChatColor.ITALIC + "Basic crafting material", Material.HORN_CORAL, 40),
            craft("alga_III", "&6[ III ] Algal", ChatColor.GRAY + "" + ChatColor.ITALIC + "Basic crafting material", Material.HORN_CORAL, 20),
            craft("pearl_I", "&9[ I ] Shiny Pearl", ChatColor.GREEN + "" + ChatColor.ITALIC + "Rare crafting material", Material.TURTLE_EGG, 8),
            craft("pearl_II", "&5[ II ] Shiny Pearl", ChatColor.GREEN + "" + ChatColor.ITALIC + "Rare crafting material", Material.TURTLE_EGG, 8),
            craft("pearl_III", "&6[ III ] Shiny Pearl", ChatColor.GREEN + "" + ChatColor.ITALIC + "Rare crafting material", Material.TURTLE_EGG, 4)
        };
        for (LootEntry e : defaults) {
            lootRepo.upsert(e);
            mirrorItemRepo.upsert(new MirrorItem(e.key(), e.category(), e.broadcast(), e.itemBase64()));
        }
    }

    private LootEntry fish(String name, Material mat, double baseWeight,
                            double minW, double maxW, double priceBase, double pricePerKg) {
        String b64 = ItemSerialization.toBase64(new ItemStack(mat));
        return new LootEntry(name, Category.FISH, baseWeight, 0, false,
            priceBase, pricePerKg, 1.0,
            1, 3, 6, 10,
            minW, maxW, b64);
    }

    private LootEntry craft(String key, String display, String lore,
                            Material mat, double baseWeight) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', display));
            meta.setLore(List.of(lore));
            meta.addEnchant(Enchantment.DURABILITY, 10, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        String b64 = ItemSerialization.toBase64(item);
        return new LootEntry(key, Category.FISHERMAN_CHEST, baseWeight, 0, false,
            0, 0, 1.0,
            1, 1, 1, 1,
            0, 0, b64);
    }
}
