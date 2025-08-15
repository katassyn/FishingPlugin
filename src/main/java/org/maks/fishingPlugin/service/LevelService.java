package org.maks.fishingPlugin.service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.fishingPlugin.data.Profile;
import org.maks.fishingPlugin.data.ProfileRepo;

/**
 * Handles rod experience calculations and persistence.
 */
public class LevelService {

  private final ProfileRepo profileRepo;
  private final Logger logger;
  private final Map<UUID, Profile> profiles = new HashMap<>();

  public LevelService(ProfileRepo profileRepo, JavaPlugin plugin) {
    this.profileRepo = profileRepo;
    this.logger = plugin.getLogger();
  }

  /**
   * Load a player profile from the database, creating a default one if missing.
   */
  public void loadProfile(Player player) {
    UUID id = player.getUniqueId();
    try {
      Profile p = profileRepo.find(id).orElse(new Profile(id, 0, 0));
      profiles.put(id, p);
    } catch (SQLException e) {
      logger.warning("Failed to load profile: " + e.getMessage());
      profiles.put(id, new Profile(id, 0, 0));
    }
  }

  /** Save the player's profile back to the database. */
  public void saveProfile(Player player) {
    Profile p = profiles.get(player.getUniqueId());
    if (p == null) {
      return;
    }
    try {
      profileRepo.upsert(p);
    } catch (SQLException e) {
      logger.warning("Failed to save profile: " + e.getMessage());
    }
  }

  private Profile profile(Player p) {
    return profiles.getOrDefault(p.getUniqueId(), new Profile(p.getUniqueId(), 0, 0));
  }

  public int getLevel(Player p) {
    return profile(p).rodLevel();
  }

  public long getXp(Player p) {
    return profile(p).rodXp();
  }

  private void set(Player p, int level, long xp) {
    profiles.put(p.getUniqueId(), new Profile(p.getUniqueId(), level, xp));
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
    set(p, level, xp);
    return level;
  }
}
