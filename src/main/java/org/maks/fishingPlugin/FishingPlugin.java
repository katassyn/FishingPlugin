package org.maks.fishingPlugin;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.fishingPlugin.listener.FishingListener;
import org.maks.fishingPlugin.listener.QteListener;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.model.ScaleConf;
import org.maks.fishingPlugin.model.ScaleMode;
import org.maks.fishingPlugin.service.LevelService;
import org.maks.fishingPlugin.service.LootService;
import org.maks.fishingPlugin.service.Awarder;
import org.maks.fishingPlugin.service.QteService;
import org.maks.fishingPlugin.service.AntiCheatService;
import org.maks.fishingPlugin.service.QuickSellService;
import org.maks.fishingPlugin.service.TeleportService;
import org.maks.fishingPlugin.service.QuestChainService;
import org.maks.fishingPlugin.command.FishingCommand;
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

    @Override
    public void onEnable() {
        // Initialize services
        this.levelService = new LevelService(this);
        saveDefaultConfig();
        this.requiredPlayerLevel = getConfig().getInt("player_level_requirement", 80);

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
                        } else { // POLY
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
        this.quickSellService = new QuickSellService(this, lootService, economy, multiplier, tax, symbol);
        this.teleportService = new TeleportService(this);
        this.antiCheatService = new AntiCheatService();
        this.qteService = new QteService(antiCheatService);
        this.questService = new QuestChainService(economy);

        // Sample loot entries to demonstrate rolling
        lootService.addEntry(new LootEntry("fish_cod", Category.FISH, 9500, 0, false, 2, 8, 1, 500, 3000, null));
        lootService.addEntry(new LootEntry("premium_shark", Category.FISH_PREMIUM, 350, 0, false, 10, 20, 1.5, 1000, 5000, null));
        lootService.addEntry(new LootEntry("ancient_rune", Category.RUNE, 80, 0, true, 0, 0, 1, 0, 0, null));

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
        getLogger().info("FishingPlugin disabled");
    }
}
