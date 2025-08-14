package org.maks.fishingPlugin.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.maks.fishingPlugin.service.QteService;

/**
 * Captures player clicks during QTE windows.
 */
public class QteListener implements Listener {

  private final QteService qte;

  public QteListener(QteService qte) {
    this.qte = qte;
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent event) {
    Action action = event.getAction();
    if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK &&
        action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    ItemStack item = event.getItem();
    if (item == null || item.getType() != Material.FISHING_ROD) {
      return;
    }
    QteService.ClickType type = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
        ? QteService.ClickType.LEFT : QteService.ClickType.RIGHT;
    qte.handleClick(event.getPlayer(), type);
  }
}
