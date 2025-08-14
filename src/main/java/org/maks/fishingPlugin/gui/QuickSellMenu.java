package org.maks.fishingPlugin.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.model.SellSummary;
import org.maks.fishingPlugin.service.QuickSellService;

public class QuickSellMenu {

  private final QuickSellService quickSellService;
  private final Map<java.util.UUID, Set<String>> selections = new HashMap<>();

  public QuickSellMenu(QuickSellService quickSellService) {
    this.quickSellService = quickSellService;
  }

  public void open(Player player) {
    SellSummary summary = quickSellService.summarize(player);
    Set<String> sel = selections.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
    Component menu = Component.text()
        .append(Component.text("Quick Sell").color(NamedTextColor.GREEN))
        .append(Component.newline());

    for (SellSummary.Entry e : summary.entries()) {
      String gk = QuickSellService.groupKey(e.key(), e.quality());
      boolean selected = sel.contains(gk);
      Component line = Component.text(e.key() + " [" + e.quality() + "] x" + e.amount() + " = "
          + quickSellService.currencySymbol() + String.format("%.2f", e.price()))
          .color(selected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
          .append(Component.text(selected ? " [Unselect]" : " [Select]")
              .color(NamedTextColor.AQUA)
              .clickEvent(ClickEvent.callback(a -> {
                if (selected) {
                  sel.remove(gk);
                } else {
                  sel.add(gk);
                }
                open(player);
              })));
      menu = menu.append(line).append(Component.newline());
    }

    menu = menu
        .append(Component.text("Total: " + quickSellService.currencySymbol()
            + String.format("%.2f", summary.totalPrice())).color(NamedTextColor.YELLOW))
        .append(Component.newline())
        .append(Component.text("[Sell Selected]").color(NamedTextColor.GOLD)
            .clickEvent(ClickEvent.callback(a -> {
              double amount = quickSellService.sellSelected(player, sel);
              player.sendMessage(Component.text("Sold fish for "
                  + quickSellService.currencySymbol() + String.format("%.2f", amount))
                  .color(NamedTextColor.YELLOW));
              sel.clear();
              open(player);
            })))
        .append(Component.space())
        .append(Component.text("[Sell All]").color(NamedTextColor.RED)
            .clickEvent(ClickEvent.callback(a -> {
              double amount = quickSellService.sellAll(player);
              player.sendMessage(Component.text("Sold fish for "
                  + quickSellService.currencySymbol() + String.format("%.2f", amount))
                  .color(NamedTextColor.YELLOW));
              sel.clear();
              open(player);
            })))
        .build();

    player.sendMessage(menu);
  }
}
