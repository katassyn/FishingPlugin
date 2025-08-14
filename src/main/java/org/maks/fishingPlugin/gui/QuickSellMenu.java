package org.maks.fishingPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.service.QuickSellService;

public class QuickSellMenu {

  private final QuickSellService quickSellService;

  public QuickSellMenu(QuickSellService quickSellService) {
    this.quickSellService = quickSellService;
  }

  public void open(Player player) {
    Component menu = Component.text()
        .append(Component.text("Quick Sell").color(NamedTextColor.GREEN))
        .append(Component.newline())
        .append(Component.text("[Sell All Fish]").color(NamedTextColor.GOLD)
            .clickEvent(ClickEvent.callback(audience -> {
              double amount = quickSellService.sellAll(player);
              player.sendMessage(Component.text("Sold fish for "
                  + quickSellService.currencySymbol()
                  + String.format("%.2f", amount)).color(NamedTextColor.YELLOW));
            })))
        .build();
    player.sendMessage(menu);
  }
}
