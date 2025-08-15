package org.maks.fishingPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.service.TeleportService;

public class MainMenu {

  private final QuickSellMenu quickSellMenu;
  private final ShopMenu shopMenu;
  private final QuestMenu questMenu;
  private final TeleportService teleportService;
  private final int requiredLevel;

  public MainMenu(QuickSellMenu quickSellMenu, ShopMenu shopMenu, QuestMenu questMenu,
      TeleportService teleportService, int requiredLevel) {
    this.quickSellMenu = quickSellMenu;
    this.shopMenu = shopMenu;
    this.questMenu = questMenu;
    this.teleportService = teleportService;
    this.requiredLevel = requiredLevel;
  }

  public void open(Player player) {
    Component menu = Component.text()
        .append(Component.text("Fishing Menu").color(NamedTextColor.AQUA))
        .append(Component.newline())
        .append(Component.text("[Quick Sell]").color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.callback(audience -> quickSellMenu.open(player))))
        .append(Component.newline())
        .append(Component.text("[Shop]").color(NamedTextColor.BLUE)
            .clickEvent(ClickEvent.callback(audience -> shopMenu.open(player))))
        .append(Component.newline())
        .append(Component.text("[Quests]").color(NamedTextColor.GOLD)
            .clickEvent(ClickEvent.callback(audience -> questMenu.open(player))))
        .append(Component.newline())
        .append(Component.text("[Teleport to fishing area]").color(NamedTextColor.DARK_GREEN)
            .clickEvent(ClickEvent.callback(audience -> {
              if (player.getLevel() < requiredLevel) {
                player.sendMessage("You need level " + requiredLevel + " to teleport.");
                return;
              }
              teleportService.teleport("fishing_main", player);
            })))
        .build();
    player.sendMessage(menu);
  }
}
