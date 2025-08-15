package org.maks.fishingPlugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.maks.fishingPlugin.service.QteService;

/**
 * Captures mouse look changes during QTE windows.
 */
public class QteListener implements Listener {

  private final QteService qte;

  public QteListener(QteService qte) {
    this.qte = qte;
  }

  @EventHandler
  public void onMove(PlayerMoveEvent event) {
    if (event.getFrom().getYaw() == event.getTo().getYaw()) {
      return; // no mouse movement
    }
    if (event.getFrom().getX() != event.getTo().getX()
        || event.getFrom().getY() != event.getTo().getY()
        || event.getFrom().getZ() != event.getTo().getZ()) {
      return; // ignore physical movement
    }
    qte.handleLook(event.getPlayer(), event.getTo().getYaw());
  }
}
