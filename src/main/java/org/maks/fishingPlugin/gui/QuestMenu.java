package org.maks.fishingPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.model.QuestProgress;
import org.maks.fishingPlugin.model.QuestStage;
import org.maks.fishingPlugin.service.QuestChainService;

/** Simple text-based quest menu. */
public class QuestMenu {

  private final QuestChainService questService;

  public QuestMenu(QuestChainService questService) {
    this.questService = questService;
  }

  public void open(Player player) {
    if (questService.isCompleted(player)) {
      player.sendMessage(Component.text("All quests completed.").color(NamedTextColor.GOLD));
      return;
    }
    QuestProgress p = questService.getProgress(player);
    QuestStage stage = questService.getCurrentStage(p.stage());
    Component.Builder menu = Component.text()
        .append(Component.text("Quest Stage " + stage.stage()).color(NamedTextColor.GOLD))
        .append(Component.newline())
        .append(Component.text("Progress: " + p.count() + "/" + stage.goal()))
        .append(Component.newline())
        .append(Component.text("Reward: $" + String.format("%.0f", stage.reward())))
        .append(Component.newline());
    if (p.count() >= stage.goal()) {
      menu.append(Component.text("[Claim Reward]").color(NamedTextColor.GREEN)
          .clickEvent(ClickEvent.callback(a -> questService.claim(player))));
    } else {
      menu.append(Component.text("Keep fishing...").color(NamedTextColor.GRAY));
    }
    player.sendMessage(menu.build());
  }
}
