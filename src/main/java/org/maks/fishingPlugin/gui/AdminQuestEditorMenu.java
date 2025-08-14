package org.maks.fishingPlugin.gui;

import java.sql.SQLException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.maks.fishingPlugin.data.QuestRepo;
import org.maks.fishingPlugin.model.QuestStage;
import org.maks.fishingPlugin.service.QuestChainService;

/** Simple chat-based quest reward editor. */
public class AdminQuestEditorMenu {

  private final QuestChainService questService;
  private final QuestRepo questRepo;

  public AdminQuestEditorMenu(QuestChainService questService, QuestRepo questRepo) {
    this.questService = questService;
    this.questRepo = questRepo;
  }

  public void open(Player player) {
    Component menu = Component.text("Quest Editor").color(NamedTextColor.GOLD);
    for (QuestStage stage : questService.getStages()) {
      Component line = Component.text()
          .append(Component.newline())
          .append(Component.text("Stage " + stage.stage() + " reward: " +
              String.format("%.0f", stage.reward())))
          .append(Component.text(" [+]").color(NamedTextColor.GREEN)
              .clickEvent(ClickEvent.callback(a -> adjust(stage, 10.0, player))))
          .append(Component.text(" [-]").color(NamedTextColor.RED)
              .clickEvent(ClickEvent.callback(a -> adjust(stage, -10.0, player))))
          .build();
      menu = menu.append(line);
    }
    player.sendMessage(menu);
  }

  private void adjust(QuestStage stage, double delta, Player player) {
    double newReward = Math.max(0, stage.reward() + delta);
    QuestStage updated = new QuestStage(stage.stage(), stage.goal(), newReward);
    questService.updateStage(updated);
    try {
      questRepo.upsert(updated);
    } catch (SQLException e) {
      player.sendMessage("DB error: " + e.getMessage());
    }
    open(player);
  }
}
