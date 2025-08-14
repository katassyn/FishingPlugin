package org.maks.fishingPlugin.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.FishingPlugin;
import org.maks.fishingPlugin.service.LevelService;
import org.maks.fishingPlugin.service.QuestChainService;

/**
 * PlaceholderAPI expansion for fishing stats.
 */
public class FishingExpansion extends PlaceholderExpansion {

    private final FishingPlugin plugin;

    public FishingExpansion(FishingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "fishing";
    }

    @Override
    public String getAuthor() {
        return String.join(",", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }
        LevelService level = plugin.getLevelService();
        QuestChainService quest = plugin.getQuestService();
        switch (params.toLowerCase()) {
            case "level":
                return String.valueOf(level.getLevel(player));
            case "xp":
                return String.valueOf(level.getXp(player));
            case "quest_stage":
                return String.valueOf(quest.getProgress(player).stage());
            default:
                return null;
        }
    }
}
