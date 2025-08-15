package org.maks.fishingPlugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.maks.fishingPlugin.model.LootEntry;
import org.maks.fishingPlugin.service.Awarder;
import org.maks.fishingPlugin.service.LevelService;
import org.maks.fishingPlugin.service.LootService;
import java.util.concurrent.ThreadLocalRandom;
import org.maks.fishingPlugin.service.QteService;
import org.maks.fishingPlugin.service.QuestChainService;
import org.maks.fishingPlugin.service.AntiCheatService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.ChatColor;
import org.maks.fishingPlugin.service.RodService;

/**
 * Listener replacing vanilla fishing drops with custom loot.
 */
public class FishingListener implements Listener {

  private final LootService lootService;
  private final Awarder awarder;
  private final LevelService levelService;
  private final QteService qteService;
  private final QuestChainService questService;
  private final int requiredLevel;
  private final AntiCheatService antiCheat;
  private final double dropMultiplier;
  private final double craftChance;
  private final RodService rodService;

  public FishingListener(LootService lootService, Awarder awarder, LevelService levelService,
      QteService qteService, QuestChainService questService, int requiredLevel,
      AntiCheatService antiCheat, double dropMultiplier, double craftChance, RodService rodService) {
    this.lootService = lootService;
    this.awarder = awarder;
    this.levelService = levelService;
    this.qteService = qteService;
    this.questService = questService;
    this.requiredLevel = requiredLevel;
    this.antiCheat = antiCheat;
    this.dropMultiplier = dropMultiplier;
    this.craftChance = craftChance;
    this.rodService = rodService;
  }

  @EventHandler
  public void onFish(PlayerFishEvent event) {
    Player player = event.getPlayer();
    if (event.getState() == PlayerFishEvent.State.FISHING) {
      if (player.getLevel() < requiredLevel) {
        event.setCancelled(true);
      }
      return;
    }
    if (event.getState() == PlayerFishEvent.State.BITE) {
      qteService.maybeStart(player); // start QTE with chance
      return;
    }
    if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
      return;
    }
    event.setCancelled(true);
    event.getHook().remove();
    if (!qteService.verify(player, player.getLocation().getYaw())) {
      return;
    }
    boolean penalized = antiCheat.consumeFlag(player.getUniqueId());
    if (penalized && ThreadLocalRandom.current().nextDouble() > dropMultiplier) {
      return;
    }
    boolean admin = rodService.isAdminRod(player.getInventory().getItemInMainHand());
    int rodLevel = admin ? 300 : levelService.getLevel(player);
    LootEntry loot;
    try {
      loot = admin ? lootService.rollAdmin() : lootService.roll(rodLevel);
    } catch (IllegalStateException e) {
      return;
    }
    Awarder.AwardResult res = awarder.give(player, loot);
    if (res.item() != null) {
      double kg = res.weightG() / 1000.0;
      levelService.awardCatchExp(player, loot.category(), kg);
      questService.onCatch(player);
      maybeGiveCraft(player);
    }
  }

  private void maybeGiveCraft(Player player) {
    if (craftChance <= 0.0) {
      return;
    }
    if (ThreadLocalRandom.current().nextDouble() >= craftChance) {
      return;
    }
    boolean algae = ThreadLocalRandom.current().nextBoolean();
    double tierRoll = ThreadLocalRandom.current().nextDouble();
    int tier = tierRoll < 0.4 ? 1 : tierRoll < 0.8 ? 2 : 3;
    int amount = algae ? ThreadLocalRandom.current().nextInt(1, 4)
        : ThreadLocalRandom.current().nextInt(1, 3);
    ItemStack item = buildCraftItem(algae, tier, amount);
    player.getInventory().addItem(item);
  }

  private ItemStack buildCraftItem(boolean algae, int tier, int amount) {
    Material mat = algae ? Material.HORN_CORAL : Material.TURTLE_EGG;
    String name = (algae ? "Alga" : "Shiny Pearl");
    ChatColor color = switch (tier) {
      case 1 -> ChatColor.BLUE;
      case 2 -> ChatColor.DARK_PURPLE;
      default -> ChatColor.GOLD;
    };
    String display = color + "[ " + roman(tier) + " ] " + name;
    String lore = algae
        ? ChatColor.GRAY + "" + ChatColor.ITALIC + "Basic crafting material"
        : ChatColor.GREEN + "" + ChatColor.ITALIC + "Rare crafting material";
    ItemStack item = new ItemStack(mat, amount);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(display);
      meta.setLore(java.util.List.of(lore));
      meta.addEnchant(Enchantment.DURABILITY, 10, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
      meta.setUnbreakable(true);
      item.setItemMeta(meta);
    }
    return item;
  }

  private String roman(int tier) {
    return switch (tier) {
      case 1 -> "I";
      case 2 -> "II";
      default -> "III";
    };
  }
}
