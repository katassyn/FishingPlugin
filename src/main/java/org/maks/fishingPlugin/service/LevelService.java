package org.maks.fishingPlugin.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.fishingPlugin.data.Profile;
import org.maks.fishingPlugin.data.ProfileRepo;
import org.maks.fishingPlugin.service.RodService;
import org.maks.fishingPlugin.model.Category;

/**
 * Handles rod experience calculations and persistence.
 */
public class LevelService {

  private final ProfileRepo profileRepo;
  private final Logger logger;
  private final Map<UUID, Profile> profiles = new HashMap<>();
  private RodService rodService;

  private final double expBase;
  private final double expCoeff;
  private final double expPower;

  private final double fishBaseXp;
  private final double fishPerKg;
  private final double chestBaseXp;
  private final double runeBaseXp;
  private final double treasureBaseXp;

  public LevelService(ProfileRepo profileRepo, JavaPlugin plugin,
      double expBase, double expCoeff, double expPower,
      double fishBaseXp, double fishPerKg,
      double chestBaseXp, double runeBaseXp, double treasureBaseXp) {
    this.profileRepo = profileRepo;
    this.logger = plugin.getLogger();
    this.expBase = expBase;
    this.expCoeff = expCoeff;
    this.expPower = expPower;
    this.fishBaseXp = fishBaseXp;
    this.fishPerKg = fishPerKg;
    this.chestBaseXp = chestBaseXp;
    this.runeBaseXp = runeBaseXp;
    this.treasureBaseXp = treasureBaseXp;
  }

  /**
   * Set the rod service used for updating item metadata. Called after
   * both services are constructed to avoid circular dependency.
   */
  public void setRodService(RodService rodService) {
    this.rodService = rodService;
  }

  /**
   * Load a player profile from the database, creating a default one if missing.
   */
  public void loadProfile(Player player) {
    UUID id = player.getUniqueId();
    try {
      Profile p = profileRepo.find(id).orElse(
          new Profile(id, 0, 0, 0, 0, 0, 0, null, Instant.now(), Instant.now()));
      profiles.put(id, p);
    } catch (SQLException e) {
      logger.warning("Failed to load profile: " + e.getMessage());
      profiles.put(id, new Profile(id, 0, 0, 0, 0, 0, 0, null, Instant.now(), Instant.now()));
    }
  }

  /** Save the player's profile back to the database. */
  public void saveProfile(Player player) {
    Profile p = profiles.get(player.getUniqueId());
    if (p == null) {
      return;
    }
    try {
      Profile withUpdated = new Profile(p.playerUuid(), p.rodLevel(), p.rodXp(),
          p.totalCatches(), p.totalWeightG(), p.largestCatchG(), p.qsEarned(),
          p.lastQteSample(), p.createdAt(), Instant.now());
      profileRepo.upsert(withUpdated);
      profiles.put(player.getUniqueId(), withUpdated);
    } catch (SQLException e) {
      logger.warning("Failed to save profile: " + e.getMessage());
    }
  }

  private Profile profile(Player p) {
    return profiles.getOrDefault(p.getUniqueId(),
        new Profile(p.getUniqueId(), 0, 0, 0, 0, 0, 0, null, Instant.now(), Instant.now()));
  }

  /** Get the stored anti-cheat sample for a player. */
  public byte[] getLastQteSample(Player p) {
    return profile(p).lastQteSample();
  }

  /** Update the stored anti-cheat sample for a player. */
  public void setLastQteSample(Player p, byte[] sample) {
    Profile old = profile(p);
    profiles.put(p.getUniqueId(),
        new Profile(p.getUniqueId(), old.rodLevel(), old.rodXp(), old.totalCatches(),
            old.totalWeightG(), old.largestCatchG(), old.qsEarned(), sample,
            old.createdAt(), Instant.now()));
  }

  public int getLevel(Player p) {
    return profile(p).rodLevel();
  }

  public long getXp(Player p) {
    return profile(p).rodXp();
  }

  private void set(Player p, int level, long xp, long totalCatches, long totalWeightG,
      long largestCatchG) {
    Profile old = profile(p);
    profiles.put(p.getUniqueId(),
        new Profile(p.getUniqueId(), level, xp, totalCatches, totalWeightG, largestCatchG,
            old.qsEarned(), old.lastQteSample(), old.createdAt(), Instant.now()));
  }

  /**
   * Calculates required experience to reach the next level.
   * The curve follows the specification without a hard cap.
   *
   * @param level current rod level
   * @return experience needed to reach {@code level + 1}
   */
  public long neededExp(int level) {
    return Math.round(expBase + expCoeff * Math.pow(level, expPower));
  }

  /**
   * Awards experience for a catch and handles level ups.
   *
   * @param p player
   * @param weightKg weight of the fish in kilograms
   * @param perfect whether the QTE was perfect
   * @return new rod level after applying experience
   */
  public int awardCatchExp(Player p, Category cat, double weightKg) {
    long gain;
    switch (cat) {
      case FISH -> gain = Math.round(fishBaseXp + fishPerKg * weightKg);
      case FISHERMAN_CHEST -> gain = Math.round(chestBaseXp);
      case RUNE -> gain = Math.round(runeBaseXp);
      case TREASURE -> gain = Math.round(treasureBaseXp);
      default -> gain = 0;
    }
    Profile prof = profile(p);
    long xp = prof.rodXp() + gain;
    int level = prof.rodLevel();
    while (xp >= neededExp(level)) {
      xp -= neededExp(level);
      level++;
    }
    long weightG = Math.round(weightKg * 1000);
    long totalCatches = prof.totalCatches() + 1;
    long totalWeight = prof.totalWeightG() + weightG;
    long largest = Math.max(prof.largestCatchG(), weightG);
    set(p, level, xp, totalCatches, totalWeight, largest);
    if (rodService != null) {
      rodService.updatePlayerRod(p, level, xp);
    }
    return level;
  }

  public long getTotalCatches(Player p) {
    return profile(p).totalCatches();
  }

  public long getTotalWeightG(Player p) {
    return profile(p).totalWeightG();
  }

  public long getLargestCatchG(Player p) {
    return profile(p).largestCatchG();
  }

  /** Increase the quick sell earnings counter for the player. */
  public void addQsEarned(Player p, long amount) {
    Profile old = profile(p);
    profiles.put(p.getUniqueId(),
        new Profile(p.getUniqueId(), old.rodLevel(), old.rodXp(), old.totalCatches(),
            old.totalWeightG(), old.largestCatchG(), old.qsEarned() + amount,
            old.lastQteSample(), old.createdAt(), Instant.now()));
  }
}
