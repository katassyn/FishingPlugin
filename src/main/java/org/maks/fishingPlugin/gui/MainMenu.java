package org.maks.fishingPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class MainMenu {

  private final QuickSellMenu quickSellMenu;
  private final RodShopMenu rodShopMenu;
  private final QuestMenu questMenu;

  public MainMenu(QuickSellMenu quickSellMenu, RodShopMenu rodShopMenu, QuestMenu questMenu) {
    this.quickSellMenu = quickSellMenu;
    this.rodShopMenu = rodShopMenu;
    this.questMenu = questMenu;
  }

  public void open(Player player) {
    Component menu = Component.text()
        .append(Component.text("Fishing Menu").color(NamedTextColor.AQUA))
        .append(Component.newline())
        .append(Component.text("[Quick Sell]").color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.callback(audience -> quickSellMenu.open(player))))
        .append(Component.newline())
        .append(Component.text("[Rod Shop]").color(NamedTextColor.BLUE)
            .clickEvent(ClickEvent.callback(audience -> rodShopMenu.open(player))))
        .append(Component.newline())
        .append(Component.text("[Quests]").color(NamedTextColor.GOLD)
            .clickEvent(ClickEvent.callback(audience -> questMenu.open(player))))
        .build();
    player.sendMessage(menu);
  }
}
