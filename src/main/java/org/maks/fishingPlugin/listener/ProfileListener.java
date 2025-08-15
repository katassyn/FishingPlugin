package org.maks.fishingPlugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.maks.fishingPlugin.service.AntiCheatService;
import org.maks.fishingPlugin.service.LevelService;

/**
 * Loads and saves player profiles on join and quit.
 */
public class ProfileListener implements Listener {

  private final LevelService levelService;
  private final AntiCheatService antiCheat;

  public ProfileListener(LevelService levelService, AntiCheatService antiCheat) {
    this.levelService = levelService;
    this.antiCheat = antiCheat;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    levelService.loadProfile(event.getPlayer());
    byte[] sample = levelService.getLastQteSample(event.getPlayer());
    antiCheat.deserialize(event.getPlayer().getUniqueId(), sample);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    byte[] sample = antiCheat.serialize(event.getPlayer().getUniqueId());
    levelService.setLastQteSample(event.getPlayer(), sample);
    levelService.saveProfile(event.getPlayer());
    antiCheat.reset(event.getPlayer().getUniqueId());
  }
}
