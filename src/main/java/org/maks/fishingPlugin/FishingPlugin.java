package org.maks.fishingPlugin;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
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
import org.maks.fishingPlugin.gui.MainMenu;
import org.maks.fishingPlugin.gui.QuickSellMenu;
import org.maks.fishingPlugin.gui.ShopMenu;
import org.maks.fishingPlugin.gui.QuestMenu;
import org.maks.fishingPlugin.gui.AdminLootEditorMenu;
import org.maks.fishingPlugin.gui.AdminQuestEditorMenu;
import org.maks.fishingPlugin.gui.PriceListMenu;
import org.maks.fishingPlugin.gui.StatsMenu;
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
        this.database = new Database(jdbcUrl, user, pass);
        DataSource ds = database.getDataSource();
        this.lootRepo = new LootRepo(ds);
        this.mirrorItemRepo = new MirrorItemRepo(ds);
        this.questRepo = new QuestRepo(ds);
        this.questProgressRepo = new QuestProgressRepo(ds);
        this.paramRepo = new ParamRepo(ds);
        this.profileRepo = new ProfileRepo(ds);
        this.levelService = new LevelService(profileRepo, this);
        this.mirrorItemService = new MirrorItemService();

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
        var scaleSec = getConfig().getConfigurationSection("rare_weight_scaling");
        if (scaleSec != null) {
            for (String catKey : scaleSec.getKeys(false)) {
                try {
                    Category cat = Category.valueOf(catKey.toUpperCase());
                    var conf = scaleSec.getConfigurationSection(catKey);
                    if (conf != null) {
                        ScaleMode mode = ScaleMode.valueOf(conf.getString("mode", "EXP").toUpperCase());
                        double param;
                        if (mode == ScaleMode.EXP) {
                            param = conf.getDouble("beta");
                        } else {
                            param = conf.getDouble("alpha");
                        }
                        double k = conf.getDouble("k", 0);
                        scaling.put(cat, new ScaleConf(mode, param, k));
                    }
                } catch (IllegalArgumentException ignored) {
                    // ignore invalid categories or modes
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
        for (Category cat : Category.values()) {
            double w = Double.parseDouble(params.getOrDefault("category_weight_" + cat.name(), "1.0"));
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
        String actionStr = acSec != null ? acSec.getString("action", "CANCEL") : "CANCEL";
        double dropMult = acSec != null ? acSec.getDouble("drop_multiplier", 0.5) : 0.5;

        this.antiCheatService = new AntiCheatService(sampleSize, toleranceMs);

        var qteSec = getConfig().getConfigurationSection("qte");
        int clicks = qteSec != null ? qteSec.getInt("clicks", 1) : 1;
        java.util.List<Long> delayRange =
            qteSec != null ? qteSec.getLongList("start_delay_ms") : java.util.List.of(650L, 1100L);
        java.util.List<Long> windowRange =
            qteSec != null ? qteSec.getLongList("window_len_ms") : java.util.List.of(350L, 700L);
        long startDelayMin = delayRange.size() > 0 ? delayRange.get(0) : 650L;
        long startDelayMax = delayRange.size() > 1 ? delayRange.get(1) : startDelayMin;
        long windowMin = windowRange.size() > 0 ? windowRange.get(0) : 350L;
        long windowMax = windowRange.size() > 1 ? windowRange.get(1) : windowMin;
        QteService.MacroAction macroAction = QteService.MacroAction.valueOf(actionStr.toUpperCase());

        this.qteService = new QteService(this, antiCheatService, clicks, startDelayMin, startDelayMax,
            windowMin, windowMax, macroAction);
        this.teleportService = new TeleportService(this);
        this.questService = new QuestChainService(economy, questRepo, questProgressRepo, this);
        if (pm.getPlugin("PlaceholderAPI") != null) {
            new org.maks.fishingPlugin.integration.FishingExpansion(this).register();
        }

        Bukkit.getPluginManager().registerEvents(new ProfileListener(levelService, antiCheatService), this);
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
        PriceListMenu priceListMenu = new PriceListMenu(lootService, quickSellService);
        StatsMenu statsMenu = new StatsMenu(levelService, lootService);
        AdminQuestEditorMenu adminQuestMenu = new AdminQuestEditorMenu(this, questService, questRepo);
        AdminLootEditorMenu adminMenu = new AdminLootEditorMenu(this, lootService, lootRepo, paramRepo,
            quickSellService, adminQuestMenu, mirrorItemRepo, mirrorItemService);
        MainMenu mainMenu = new MainMenu(quickSellMenu, shopMenu, questMenu, priceListMenu, statsMenu,
            teleportService, requiredPlayerLevel);
        getCommand("fishing").setExecutor(new FishingCommand(mainMenu, adminMenu, requiredPlayerLevel));

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
}
