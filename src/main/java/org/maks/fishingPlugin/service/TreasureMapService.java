package org.maks.fishingPlugin.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;

/**
 * Handles treasure map identification and metadata.
 */
public class TreasureMapService {

  public enum MapState { UNIDENTIFIED, IDENTIFIED, ASH, SPENT }

  public enum Lair { INFERNAL, HELL, BLOOD, KRAKEN }

  private record Weighted(Lair lair, double weight) {}

  private final Economy economy;
  private final NamespacedKey idKey;
  private final NamespacedKey stateKey;
  private final NamespacedKey lairKey;
  private final double identifyCost;
  private final String currencySymbol;
  private final List<Weighted> weights = new ArrayList<>();
  private double totalWeight;
  private final String unidentifiedName;
  private final List<String> unidentifiedLore;
  private final String ashName;
  private final List<String> ashLore;
  private final String identifiedNameFormat;
  private final String identifiedLoreHeader;
  private final Map<Lair, List<String>> lairLore = new EnumMap<>(Lair.class);
  private final String msgNotEnoughMoney;
  private final String msgIdentifySuccess;
  private final String msgIdentifyEmpty;
  private final Sound identifySuccessSound;
  private final Sound identifyEmptySound;
  private final Random random = new Random();
  private final org.maks.fishingPlugin.data.TreasureMapRepo repo;

  public TreasureMapService(JavaPlugin plugin, Economy economy, org.maks.fishingPlugin.data.TreasureMapRepo repo) {
    this.economy = economy;
    this.repo = repo;
    this.idKey = new NamespacedKey(plugin, "map_id");
    this.stateKey = new NamespacedKey(plugin, "map_state");
    this.lairKey = new NamespacedKey(plugin, "map_lair");
    ConfigurationSection sec = plugin.getConfig().getConfigurationSection("treasure_maps");
    if (sec == null) {
      throw new IllegalStateException("treasure_maps section missing");
    }
    this.identifyCost = sec.getDouble("identify_cost", 0);
    this.currencySymbol = plugin.getConfig().getString("economy.currency_symbol", "$");

    ConfigurationSection outcomes = sec.getConfigurationSection("outcomes");
    if (outcomes != null) {
      for (String key : outcomes.getKeys(false)) {
        double w = outcomes.getDouble(key);
        if (key.equalsIgnoreCase("EMPTY")) {
          weights.add(new Weighted(null, w));
        } else {
          try {
            Lair l = Lair.valueOf(key.toUpperCase());
            weights.add(new Weighted(l, w));
          } catch (IllegalArgumentException ignored) {
          }
        }
        totalWeight += w;
      }
    }

    ConfigurationSection items = sec.getConfigurationSection("items");
    this.unidentifiedName = items != null ? items.getString("unidentified_name", "Unidentified Map") : "Unidentified Map";
    this.unidentifiedLore = items != null ? items.getStringList("unidentified_lore") : List.of();
    this.ashName = items != null ? items.getString("ash_name", "Ash Map") : "Ash Map";
    this.ashLore = items != null ? items.getStringList("ash_lore") : List.of();
    this.identifiedNameFormat = items != null ? items.getString("identified_name_format", "{lair} Map") : "{lair} Map";
    this.identifiedLoreHeader = items != null ? items.getString("identified_lore_header", "-") : "-";

    ConfigurationSection lairSec = sec.getConfigurationSection("lairs");
    if (lairSec != null) {
      for (String key : lairSec.getKeys(false)) {
        try {
          Lair l = Lair.valueOf(key.toUpperCase());
          lairLore.put(l, lairSec.getStringList(key + ".lore_lines"));
        } catch (IllegalArgumentException ignored) {
        }
      }
    }

    ConfigurationSection msgSec = sec.getConfigurationSection("messages");
    this.msgNotEnoughMoney = msgSec != null ? msgSec.getString("not_enough_money", "") : "";
    this.msgIdentifySuccess = msgSec != null ? msgSec.getString("identify_success", "") : "";
    this.msgIdentifyEmpty = msgSec != null ? msgSec.getString("identify_empty", "") : "";

    ConfigurationSection effSec = sec.getConfigurationSection("effects");
    this.identifySuccessSound = parseSound(effSec != null ? effSec.getString("on_identify_success_sound") : null);
    this.identifyEmptySound = parseSound(effSec != null ? effSec.getString("on_identify_empty_sound") : null);
  }

  private String color(String s) {
    return ChatColor.translateAlternateColorCodes('&', s);
  }

  private Sound parseSound(String name) {
    if (name == null || name.isEmpty()) return null;
    try {
      return Sound.valueOf(name);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /** Create a new unidentified treasure map item. */
  public ItemStack createUnidentified() {
    ItemStack item = new ItemStack(Material.PAPER);
    applyUnidentified(item);
    UUID id = getId(item);
    if (id != null) {
      try {
        repo.upsert(id, MapState.UNIDENTIFIED, null);
      } catch (Exception ignored) {}
    }
    return item;
  }

  private void ensureId(ItemMeta meta) {
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    if (pdc.get(idKey, PersistentDataType.STRING) == null) {
      pdc.set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
    }
  }

  private UUID getId(ItemMeta meta) {
    String id = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    if (id == null) return null;
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void applyUnidentified(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return;
    ensureId(meta);
    meta.setDisplayName(color(unidentifiedName));
    List<String> lore = new ArrayList<>();
    for (String line : unidentifiedLore) lore.add(color(line));
    meta.setLore(lore);
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(stateKey, PersistentDataType.STRING, MapState.UNIDENTIFIED.name());
    pdc.remove(lairKey);
    meta.setUnbreakable(true);
    meta.addEnchant(Enchantment.DURABILITY, 10, true);
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
    item.setItemMeta(meta);
  }

  private void applyAsh(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return;
    ensureId(meta);
    meta.setDisplayName(color(ashName));
    List<String> lore = new ArrayList<>();
    for (String line : ashLore) lore.add(color(line));
    meta.setLore(lore);
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(stateKey, PersistentDataType.STRING, MapState.ASH.name());
    pdc.remove(lairKey);
    meta.setUnbreakable(true);
    meta.addEnchant(Enchantment.DURABILITY, 10, true);
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
    item.setItemMeta(meta);
  }

  private void applyIdentified(ItemStack item, Lair lair) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return;
    ensureId(meta);
    String name = identifiedNameFormat.replace("{lair}", lairDisplay(lair));
    meta.setDisplayName(color(name));
    List<String> lore = new ArrayList<>();
    lore.add(color(identifiedLoreHeader));
    List<String> lines = lairLore.getOrDefault(lair, List.of());
    for (String line : lines) lore.add(color(line));
    meta.setLore(lore);
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(stateKey, PersistentDataType.STRING, MapState.IDENTIFIED.name());
    pdc.set(lairKey, PersistentDataType.STRING, lair.name());
    meta.setUnbreakable(true);
    meta.addEnchant(Enchantment.DURABILITY, 10, true);
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
    item.setItemMeta(meta);
  }

  private Lair rollLair() {
    double r = random.nextDouble() * totalWeight;
    double acc = 0;
    for (Weighted w : weights) {
      acc += w.weight;
      if (r < acc) return w.lair;
    }
    return null;
  }

  /** Human readable lair name. */
  public String lairDisplay(Lair lair) {
    return switch (lair) {
      case INFERNAL -> "Infernal Lair";
      case HELL -> "Hell Lair";
      case BLOOD -> "Blood Lair";
      case KRAKEN -> "Kraken's Lair";
    };
  }

  private MapState readState(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return null;
    String state = meta.getPersistentDataContainer().get(stateKey, PersistentDataType.STRING);
    if (state == null) return null;
    try {
      return MapState.valueOf(state);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private boolean loreMatches(List<String> actual, List<String> expected) {
    if (actual == null || actual.size() < expected.size()) return false;
    for (int i = 0; i < expected.size(); i++) {
      String a = ChatColor.stripColor(actual.get(i));
      String e = ChatColor.stripColor(color(expected.get(i)));
      if (!a.equals(e)) return false;
    }
    return true;
  }

  private MapState inferAndApplyState(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null || !meta.hasDisplayName()) return null;
    String name = ChatColor.stripColor(meta.getDisplayName());
    if (ChatColor.stripColor(color(unidentifiedName)).equals(name)
        && loreMatches(meta.getLore(), unidentifiedLore)) {
      applyUnidentified(item);
      return MapState.UNIDENTIFIED;
    }
    if (ChatColor.stripColor(color(ashName)).equals(name)
        && loreMatches(meta.getLore(), ashLore)) {
      applyAsh(item);
      return MapState.ASH;
    }
    for (Lair l : Lair.values()) {
      String expectedName =
          ChatColor.stripColor(color(identifiedNameFormat.replace("{lair}", lairDisplay(l))));
      if (expectedName.equals(name)) {
        List<String> lore = new ArrayList<>();
        lore.add(identifiedLoreHeader);
        lore.addAll(lairLore.getOrDefault(l, List.of()));
        if (loreMatches(meta.getLore(), lore)) {
          applyIdentified(item, l);
          return MapState.IDENTIFIED;
        }
      }
    }
    return null;
  }

  public MapState getState(ItemStack item) {
    MapState state = readState(item);
    if (state != null) return state;
    return inferAndApplyState(item);
  }

  public Lair getLair(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return null;
    String lair = meta.getPersistentDataContainer().get(lairKey, PersistentDataType.STRING);
    if (lair == null) return null;
    try {
      return Lair.valueOf(lair);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  public boolean isUnidentified(ItemStack item) {
    return getState(item) == MapState.UNIDENTIFIED;
  }

  public boolean isIdentified(ItemStack item) {
    return getState(item) == MapState.IDENTIFIED;
  }

  public boolean isAsh(ItemStack item) {
    return getState(item) == MapState.ASH;
  }

  public UUID getId(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return null;
    return getId(meta);
  }

  /** Mark map as spent in persistent storage. */
  public void markSpent(ItemStack item) {
    UUID id = getId(item);
    if (id != null) {
      try {
        repo.upsert(id, MapState.SPENT, getLair(item));
      } catch (Exception ignored) {}
    }
  }

  /** Identify the map in-place and charge the player. */
  public void identify(Player player, ItemStack item) {
    if (!isUnidentified(item)) {
      return;
    }
    if (economy.getBalance(player) < identifyCost) {
      String msg = msgNotEnoughMoney.replace("${cost}", currencySymbol + identifyCost);
      player.sendMessage(color(msg));
      return;
    }
    economy.withdrawPlayer(player, identifyCost);
    Lair result = rollLair();
    if (result == null) {
      item.setType(Material.AIR);
      player.sendMessage(color(msgIdentifyEmpty));
      if (identifyEmptySound != null)
        player.playSound(player.getLocation(), identifyEmptySound, 1f, 1f);
      UUID id = getId(item);
      if (id != null) {
        try { repo.upsert(id, MapState.ASH, null); } catch (Exception ignored) {}
      }
    } else {
      applyIdentified(item, result);
      String msg = msgIdentifySuccess.replace("{lair}", lairDisplay(result));
      player.sendMessage(color(msg));
      if (identifySuccessSound != null)
        player.playSound(player.getLocation(), identifySuccessSound, 1f, 1f);
      UUID id = getId(item);
      if (id != null) {
        try { repo.upsert(id, MapState.IDENTIFIED, result); } catch (Exception ignored) {}
      }
    }
  }

  public double identifyCost() {
    return identifyCost;
  }

  public String currencySymbol() {
    return currencySymbol;
  }

  // Debug helpers exposing expected unidentified map metadata
  public String debugUnidentifiedName() {
    return unidentifiedName;
  }

  public List<String> debugUnidentifiedLore() {
    return unidentifiedLore;
  }
}

