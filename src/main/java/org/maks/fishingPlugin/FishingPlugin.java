package org.maks.fishingPlugin;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.flywaydb.core.Flyway;
import org.maks.fishingPlugin.command.FishingCommand;
import org.maks.fishingPlugin.data.Database;
import org.maks.fishingPlugin.data.LootRepo;
import org.maks.fishingPlugin.data.ParamRepo;
import org.maks.fishingPlugin.data.ProfileRepo;
import org.maks.fishingPlugin.data.QuestRepo;
import org.maks.fishingPlugin.listener.FishingListener;
import org.maks.fishingPlugin.listener.QteListener;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.model.ScaleConf;
import org.maks.fishingPlugin.model.ScaleMode;
import org.maks.fishingPlugin.service.AntiCheatService;
import org.maks.fishingPlugin.service.Awarder;
import org.maks.fishingPlugin.service.LevelService;
import org.maks.fishingPlugin.service.LootService;
import org.maks.fishingPlugin.service.QteService;
import org.maks.fishingPlugin.service.QuestChainService;
import org.maks.fishingPlugin.service.QuickSellService;
import org.maks.fishingPlugin.service.TeleportService;
import net.milkbowl.vault.economy.Economy;

public final class FishingPlugin extends JavaPlugin {

    private LevelService levelService;
    private LootService lootService;
    private Awarder awarder;
    private QuickSellService quickSellService;
    private TeleportService teleportService;
    private QteService qteService;
    private AntiCheatService antiCheatService;
    private QuestChainService questService;
    private Economy economy;
    private int requiredPlayerLevel;
    private Database database;
    private LootRepo lootRepo;
    private QuestRepo questRepo;
    private ParamRepo paramRepo;
    private ProfileRepo profileRepo;

    @Override
    public void onEnable() {
        this.levelService = new LevelService(this);
        saveDefaultConfig();

        this.database = new Database("jdbc:h2:./data/fishing", "sa", "");
        DataSource ds = database.getDataSource();
        Flyway.configure().dataSource(ds).load().migrate();
        this.lootRepo = new LootRepo(ds);
        this.questRepo = new QuestRepo(ds);
        this.paramRepo = new ParamRepo(ds);
        this.profileRepo = new ProfileRepo(ds);

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
        this.lootService = new LootService(scaling);
        try {
            for (LootEntry entry : lootRepo.findAll()) {
                lootService.addEntry(entry);
            }
        } catch (SQLException e) {
            getLogger().warning("Failed to load loot entries: " + e.getMessage());
        }
        this.economy = Bukkit.getServicesManager().getRegistration(Economy.class).getProvider();
        this.awarder = new Awarder(this);
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
        this.quickSellService = new QuickSellService(this, lootService, economy, multiplier, tax, symbol);
        this.teleportService = new TeleportService(this);
        this.antiCheatService = new AntiCheatService();
        this.qteService = new QteService(antiCheatService);
        this.questService = new QuestChainService(economy);
        try {
            questService.setStages(questRepo.findAll());
        } catch (SQLException e) {
            getLogger().warning("Failed to load quest stages: " + e.getMessage());
        }

        Bukkit.getPluginManager().registerEvents(
            new FishingListener(lootService, awarder, levelService, qteService, questService, requiredPlayerLevel), this);
        Bukkit.getPluginManager().registerEvents(new QteListener(qteService), this);
        getCommand("fishing").setExecutor(
            new FishingCommand(quickSellService, teleportService, questService, levelService, requiredPlayerLevel));

        getLogger().info("FishingPlugin enabled");
    }

    public LevelService getLevelService() {
        return levelService;
    }

    public LootService getLootService() {
        return lootService;
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        getLogger().info("FishingPlugin disabled");
    }
}
