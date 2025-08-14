package org.maks.fishingPlugin.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles rod experience calculations and persistence.
 */
public class LevelService {

  private final NamespacedKey levelKey;
  private final NamespacedKey xpKey;

  public LevelService(JavaPlugin plugin) {
    this.levelKey = new NamespacedKey(plugin, "rod_level");
    this.xpKey = new NamespacedKey(plugin, "rod_xp");
  }

  /**
   * Calculates required experience to reach the next level.
   * The curve follows the specification without a hard cap.
   *
   * @param level current rod level
   * @return experience needed to reach {@code level + 1}
   */
  public long neededExp(int level) {
    if (level < 15) {
      return Math.round(150 * Math.pow(1.12, level));
    }
    if (level < 30) {
      return Math.round(150 * Math.pow(1.12, 15) * Math.pow(1.14, level - 15));
    }
    return Math.round(150 * Math.pow(1.12, 15) * Math.pow(1.14, 15) * Math.pow(1.16, level - 30));
  }

  private PersistentDataContainer data(Player p) {
    return p.getPersistentDataContainer();
  }

  public int getLevel(Player p) {
    return data(p).getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
  }

  public long getXp(Player p) {
    return data(p).getOrDefault(xpKey, PersistentDataType.LONG, 0L);
  }

  private void setLevel(Player p, int level) {
    data(p).set(levelKey, PersistentDataType.INTEGER, level);
  }

  private void setXp(Player p, long xp) {
    data(p).set(xpKey, PersistentDataType.LONG, xp);
  }

  /**
   * Awards experience for a catch and handles level ups.
   *
   * @param p player
   * @param weightKg weight of the fish in kilograms
   * @param perfect whether the QTE was perfect
   * @return new rod level after applying experience
   */
  public int awardCatchExp(Player p, double weightKg, boolean perfect) {
    long gain = Math.round(8 + 0.4 * weightKg + (perfect ? 3 : 0));
    long xp = getXp(p) + gain;
    int level = getLevel(p);
    while (xp >= neededExp(level)) {
      xp -= neededExp(level);
      level++;
    }
    setXp(p, xp);
    setLevel(p, level);
    return level;
  }
}

