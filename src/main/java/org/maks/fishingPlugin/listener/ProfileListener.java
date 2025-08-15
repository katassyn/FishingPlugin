package org.maks.fishingPlugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.maks.fishingPlugin.service.LevelService;

/**
 * Loads and saves player profiles on join and quit.
 */
public class ProfileListener implements Listener {

  private final LevelService levelService;

  public ProfileListener(LevelService levelService) {
    this.levelService = levelService;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    levelService.loadProfile(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    levelService.saveProfile(event.getPlayer());
  }
}
