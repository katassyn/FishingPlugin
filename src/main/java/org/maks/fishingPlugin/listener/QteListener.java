package org.maks.fishingPlugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.maks.fishingPlugin.service.QteService;

/** Captures movement during QTE windows. */
public class QteListener implements Listener {

  private final QteService qte;

  public QteListener(QteService qte) {
    this.qte = qte;
  }

  @EventHandler
  public void onMove(PlayerMoveEvent event) {
    if (event.getFrom().getBlockX() != event.getTo().getBlockX()
        || event.getFrom().getBlockY() != event.getTo().getBlockY()
        || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
      qte.fail(event.getPlayer());
    }
  }

}
