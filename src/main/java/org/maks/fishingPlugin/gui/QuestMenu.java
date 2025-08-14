package org.maks.fishingPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.service.QuestChainService;

public class QuestMenu {

  private final QuestChainService questService;

  public QuestMenu(QuestChainService questService) {
    this.questService = questService;
  }

  public void open(Player player) {
    Component menu = Component.text()
        .append(Component.text("Quests").color(NamedTextColor.GOLD))
        .append(Component.newline())
        .append(Component.text("[Claim Reward]").color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.callback(audience -> questService.claim(player))))
        .build();
    player.sendMessage(menu);
  }
}
