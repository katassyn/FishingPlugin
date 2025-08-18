package org.maks.fishingPlugin.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import org.maks.fishingPlugin.service.BountyRewardService;

/**
 * Handles bounty confirmations and lair instances.
 */
public class BountyService implements Listener {

  private static final int SUCCESS_COUNTDOWN_SECONDS = 15;

  public record SpawnSpec(int count, List<String> bossPool, String cmdTemplate, int delayTicks) {}
  public record LairSpec(String warp, int timeLimitSec, SpawnSpec spawn) {}

  private final JavaPlugin plugin;
  private final TeleportService teleportService;
  private final TreasureMapService mapService;
  private final org.maks.fishingPlugin.data.LairLockRepo lockRepo;
  private final BountyRewardService rewardService;
  private final Map<TreasureMapService.Lair, LairSpec> lairSpecs = new EnumMap<>(TreasureMapService.Lair.class);
  private final Map<TreasureMapService.Lair, UUID> occupied = new EnumMap<>(TreasureMapService.Lair.class);
  private final Map<UUID, TreasureMapService.Lair> playerLair = new HashMap<>();
  private final Map<UUID, BossBar> bars = new HashMap<>();
  private final Map<UUID, Integer> barTasks = new HashMap<>();
  private final Map<UUID, Integer> timeoutTasks = new HashMap<>();
  private final Map<UUID, Map<String, Integer>> activeMobs = new HashMap<>();
  private final Random random = new Random();

  private final String msgConfirmStart;
  private final String msgDiscard;
  private final String msgLairOccupied;
  private final String msgTimeout;
  private final String msgDeath;
  private final String msgAshCannotUse;
  private final String msgWarpFailed;
  private final String msgLairReleased;
  private final String msgSuccess;
  private final String titleStart;
  private final String titleStartSub;
  private final String titleTimeout;
  private final String titleTimeoutSub;
  private final String titleDeath;
  private final String titleDeathSub;
  private final String titleSuccess;
  private final String titleSuccessSub;
  private final Sound confirmSound;

  public BountyService(JavaPlugin plugin, TeleportService teleportService, TreasureMapService mapService,
      org.maks.fishingPlugin.data.LairLockRepo lockRepo, BountyRewardService rewardService) {
    this.plugin = plugin;
    this.teleportService = teleportService;
    this.mapService = mapService;
    this.lockRepo = lockRepo;
    this.rewardService = rewardService;

    var lairSec = plugin.getConfig().getConfigurationSection("treasure_maps.lairs");
    if (lairSec != null) {
      for (String key : lairSec.getKeys(false)) {
        try {
          TreasureMapService.Lair lair = TreasureMapService.Lair.valueOf(key.toUpperCase());
          String warp = lairSec.getString(key + ".warp", "");
          int limit = lairSec.getInt(key + ".time_limit_seconds", 600);
          var spawnSec = lairSec.getConfigurationSection(key + ".spawn");
          int count = spawnSec != null ? spawnSec.getInt("count", 1) : 1;
          List<String> pool = spawnSec != null ? spawnSec.getStringList("boss_pool") : List.of();
          String cmd = spawnSec != null ? spawnSec.getString("spawn_cmd_template", "") : "";
          int delay = spawnSec != null ? spawnSec.getInt("spawn_delay_ticks", 20) : 20;
          lairSpecs.put(lair, new LairSpec(warp, limit, new SpawnSpec(count, pool, cmd, delay)));
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    var msgSec = plugin.getConfig().getConfigurationSection("treasure_maps.messages");
    this.msgConfirmStart = msgSec != null ? msgSec.getString("confirm_start", "") : "";
    this.msgDiscard = msgSec != null ? msgSec.getString("discard", "") : "";
    this.msgLairOccupied = msgSec != null ? msgSec.getString("lair_occupied", "") : "";
    this.msgTimeout = msgSec != null ? msgSec.getString("timeout", "") : "";
    this.msgDeath = msgSec != null ? msgSec.getString("death", "") : "";
    this.msgAshCannotUse = msgSec != null ? msgSec.getString("ash_cannot_use", "") : "";
    this.msgWarpFailed = msgSec != null ? msgSec.getString("warp_failed", "") : "";
    this.msgLairReleased = msgSec != null ? msgSec.getString("lair_released", "") : "";
    this.msgSuccess = msgSec != null ? msgSec.getString("success", "") : "";
    var titleSec = plugin.getConfig().getConfigurationSection("treasure_maps.titles");
    this.titleStart = titleSec != null ? titleSec.getString("start_title", "") : "";
    this.titleStartSub = titleSec != null ? titleSec.getString("start_subtitle", "") : "";
    this.titleTimeout = titleSec != null ? titleSec.getString("timeout_title", "") : "";
    this.titleTimeoutSub = titleSec != null ? titleSec.getString("timeout_subtitle", "") : "";
    this.titleDeath = titleSec != null ? titleSec.getString("death_title", "") : "";
    this.titleDeathSub = titleSec != null ? titleSec.getString("death_subtitle", "") : "";
    this.titleSuccess = titleSec != null ? titleSec.getString("success_title", "") : "";
    this.titleSuccessSub = titleSec != null ? titleSec.getString("success_subtitle", "") : "";
    var effSec = plugin.getConfig().getConfigurationSection("treasure_maps.effects");
    this.confirmSound = parseSound(effSec != null ? effSec.getString("on_confirm_sound") : null);

    try {
      long cutoff = System.currentTimeMillis() - 15 * 60_000L;
      lockRepo.cleanupOlderThan(cutoff);
      for (var lock : lockRepo.findAll()) {
        occupied.put(lock.lair(), lock.playerUuid());
        playerLair.put(lock.playerUuid(), lock.lair());
      }
    } catch (Exception e) {
      plugin.getLogger().warning("Failed to load lair locks: " + e.getMessage());
    }
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

  public String ashMessage() {
    return color(msgAshCannotUse);
  }

  public boolean isOccupied(TreasureMapService.Lair lair) {
    return occupied.containsKey(lair);
  }

  public void discard(Player player, ItemStack map) {
    player.closeInventory();
    var leftovers = player.getInventory().addItem(map);
    for (ItemStack drop : leftovers.values()) {
      player.getWorld().dropItem(player.getLocation(), drop);
    }
    player.sendMessage(color(msgDiscard));
  }

  public boolean confirm(Player player, ItemStack map) {
    TreasureMapService.Lair lair = mapService.getLair(map);
    if (lair == null) {
      return false;
    }
    UUID mapId = mapService.getId(map);
    if (mapId == null) {
      return false;
    }
    if (!lockAttempt(lair, player.getUniqueId(), mapId)) {
      player.sendMessage(color(msgLairOccupied));
      return false;
    }
    LairSpec spec = lairSpecs.get(lair);
    if (spec == null) {
      freeLair(lair);
      return false;
    }
    occupied.put(lair, player.getUniqueId());
    playerLair.put(player.getUniqueId(), lair);
    mapService.markSpent(map);
    map.setType(Material.AIR);
    map.setAmount(0);
    if (!teleportService.teleport(spec.warp(), player)) {
      freeLair(lair);
      playerLair.remove(player.getUniqueId());
      player.sendMessage(color(msgWarpFailed));
      return false;
    }
    String lairName = mapService.lairDisplay(lair);
    player.sendMessage(color(msgConfirmStart.replace("{lair}", lairName)));
    if (confirmSound != null) {
      player.playSound(player.getLocation(), confirmSound, 1f, 1f);
    }
    player.sendTitle(color(titleStart.replace("{lair}", lairName)),
        color(titleStartSub.replace("{lair}", lairName)), 10, 60, 10);

    int time = spec.timeLimitSec();
    String initTime = String.format("%d:%02d", time / 60, time % 60);
    BossBar bar = Bukkit.createBossBar(color(lairName + " - " + initTime), BarColor.RED, BarStyle.SOLID);
    bar.addPlayer(player);
    bars.put(player.getUniqueId(), bar);
    final int[] remaining = {time};
    int barTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
      remaining[0]--;
      double prog = remaining[0] / (double) time;
      bar.setProgress(Math.max(0, prog));
      bar.setTitle(color(String.format("%s - %d:%02d", lairName, remaining[0] / 60, remaining[0] % 60)));
      if (remaining[0] <= 0) {
        bar.removeAll();
      }
    }, 20L, 20L);
    barTasks.put(player.getUniqueId(), barTask);
    int timeoutTask = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> timeout(player.getUniqueId()), time * 20L);
    timeoutTasks.put(player.getUniqueId(), timeoutTask);
    Bukkit.getScheduler().runTaskLater(plugin, () -> spawnBosses(player, spec.spawn()), spec.spawn().delayTicks());
    return true;
  }

  private boolean lockAttempt(TreasureMapService.Lair lair, UUID player, UUID mapId) {
    try {
      return lockRepo.tryLock(lair, player, mapId);
    } catch (Exception e) {
      plugin.getLogger().warning("Failed to lock lair: " + e.getMessage());
      return false;
    }
  }

  private void freeLair(TreasureMapService.Lair lair) {
    occupied.remove(lair);
    try {
      lockRepo.release(lair);
    } catch (Exception e) {
      plugin.getLogger().warning("Failed to release lair: " + e.getMessage());
    }
    if (!msgLairReleased.isEmpty()) {
      Bukkit.broadcastMessage(color(msgLairReleased.replace("{lair}", mapService.lairDisplay(lair))));
    }
  }

  private void cancelTasks(UUID playerId) {
    Integer bt = barTasks.remove(playerId);
    if (bt != null) Bukkit.getScheduler().cancelTask(bt);
    Integer tt = timeoutTasks.remove(playerId);
    if (tt != null) Bukkit.getScheduler().cancelTask(tt);
    BossBar bar = bars.remove(playerId);
    if (bar != null) bar.removeAll();
  }

  private void spawnBosses(Player player, SpawnSpec spawn) {
    Location loc = player.getLocation();
    Map<String, Integer> counts = new HashMap<>();
    for (int i = 0; i < spawn.count(); i++) {
      if (spawn.bossPool().isEmpty()) break;
      String mob = spawn.bossPool().get(random.nextInt(spawn.bossPool().size()));
      counts.merge(mob, 1, Integer::sum);
      String cmd = spawn.cmdTemplate()
          .replace("{mob}", mob)
          .replace("{world}", loc.getWorld().getName())
          .replace("{x}", String.valueOf(loc.getBlockX()))
          .replace("{y}", String.valueOf(loc.getBlockY()))
          .replace("{z}", String.valueOf(loc.getBlockZ()));
      plugin.getLogger().info("Executing MythicMobs spawn command: " + cmd);

      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
    if (!counts.isEmpty()) {
      activeMobs.put(player.getUniqueId(), counts);
    }
  }

  private void timeout(UUID playerId) {
    release(playerId, msgTimeout, titleTimeout, titleTimeoutSub);
  }

  private void success(UUID playerId) {
    cancelTasks(playerId);
    activeMobs.remove(playerId);
    Player p = Bukkit.getPlayer(playerId);
    if (p == null || !p.isOnline()) {
      Bukkit.getScheduler().runTask(plugin, () -> release(playerId, msgSuccess, null, null));
      return;
    }

    TreasureMapService.Lair lair = playerLair.get(playerId);
    String lairName = lair != null ? mapService.lairDisplay(lair) : "";
    if (lair != null) {
      rewardService.give(p, lair);
    }
    final int[] remaining = {SUCCESS_COUNTDOWN_SECONDS};
    final int[] taskId = new int[1];
    taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
      p.sendTitle(color(titleSuccess.replace("{lair}", lairName)),
          color(titleSuccessSub.replace("{lair}", lairName)
              .replace("{time}", String.valueOf(remaining[0]))), 0, 20, 0);
      remaining[0]--;
      if (remaining[0] < 0) {
        Bukkit.getScheduler().cancelTask(taskId[0]);
        timeoutTasks.remove(playerId);
        release(playerId, msgSuccess, null, null);
      }
    }, 0L, 20L);
    timeoutTasks.put(playerId, taskId[0]);
  }

  private void release(UUID playerId, String message, String title, String subtitle) {
    TreasureMapService.Lair lair = playerLair.remove(playerId);
    if (lair != null) {
      freeLair(lair);
      cancelTasks(playerId);
      activeMobs.remove(playerId);
      Player p = Bukkit.getPlayer(playerId);
      if (p != null && p.isOnline()) {
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + p.getName())) {
          p.teleport(p.getWorld().getSpawnLocation());
        }
        p.sendMessage(color(message));
        if (title != null && subtitle != null) {
          String lairName = mapService.lairDisplay(lair);
          p.sendTitle(color(title.replace("{lair}", lairName)),
              color(subtitle.replace("{lair}", lairName)), 10, 60, 10);
        }
      }
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    release(e.getPlayer().getUniqueId(), msgTimeout, null, null);
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent e) {
    release(e.getEntity().getUniqueId(), msgDeath, titleDeath, titleDeathSub);
  }

  @EventHandler
  public void onMobDeath(io.lumine.mythic.bukkit.events.MythicMobDeathEvent e) {
    var killerLE = e.getKiller();            // LivingEntity albo null
    if (!(killerLE instanceof Player killer)) {
      return;
    }

    Map<String, Integer> counts = activeMobs.get(killer.getUniqueId());
    if (counts == null) return;

    String type = e.getMobType().getInternalName(); // albo e.getMob().getType().getInternalName()
    Integer remaining = counts.get(type);
    if (remaining == null) return;

    if (remaining <= 1) {
      counts.remove(type);
    } else {
      counts.put(type, remaining - 1);
    }

    if (counts.isEmpty()) {
      success(killer.getUniqueId());
    }
  }


}
