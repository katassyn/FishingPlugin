package org.maks.fishingPlugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.maks.fishingPlugin.service.AntiCheatService;
import org.maks.fishingPlugin.service.LevelService;
import org.maks.fishingPlugin.service.RodService;

/**
 * Loads and saves player profiles on join and quit.
 */
public class ProfileListener implements Listener {

  private final LevelService levelService;
  private final AntiCheatService antiCheat;
  private final RodService rodService;

  public ProfileListener(LevelService levelService, AntiCheatService antiCheat,
      RodService rodService) {
    this.levelService = levelService;
    this.antiCheat = antiCheat;
    this.rodService = rodService;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    levelService.loadProfile(event.getPlayer());
    byte[] sample = levelService.getLastQteSample(event.getPlayer());
    antiCheat.deserialize(event.getPlayer().getUniqueId(), sample);
    rodService.updatePlayerRod(event.getPlayer(), levelService.getLevel(event.getPlayer()),
        levelService.getXp(event.getPlayer()));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    byte[] sample = antiCheat.serialize(event.getPlayer().getUniqueId());
    levelService.setLastQteSample(event.getPlayer(), sample);
    levelService.saveProfile(event.getPlayer());
    antiCheat.reset(event.getPlayer().getUniqueId());
  }
}
