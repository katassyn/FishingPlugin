package org.maks.fishingPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class RodShopMenu {

  public void open(Player player) {
    player.sendMessage(Component.text("Rod shop is not implemented yet.")
        .color(NamedTextColor.GRAY));
  }
}
