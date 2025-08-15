package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.maks.fishingPlugin.model.QuestStage;

/** Repository for quest stage definitions. */
public class QuestRepo {

  private final DataSource dataSource;

  public QuestRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Create backing table if it doesn't exist. */
  public void init() throws SQLException {
    String sql = "CREATE TABLE IF NOT EXISTS fishing_quest (" +
        "stage INT PRIMARY KEY, " +
        "title VARCHAR(255) NOT NULL, " +
        "lore TEXT, " +
        "goal_type VARCHAR(32) NOT NULL, " +
        "goal INT NOT NULL, " +
        "reward_type VARCHAR(32) NOT NULL, " +
        "reward DOUBLE NOT NULL, " +
        "reward_data TEXT" +
        ")";
    try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  public List<QuestStage> findAll() throws SQLException {
    String sql =
        "SELECT stage, title, lore, goal_type, goal, reward_type, reward, reward_data FROM fishing_quest ORDER BY stage";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      List<QuestStage> list = new ArrayList<>();
      while (rs.next()) {
        QuestStage.GoalType goalType =
            QuestStage.GoalType.valueOf(rs.getString("goal_type"));
        QuestStage.RewardType rewardType =
            QuestStage.RewardType.valueOf(rs.getString("reward_type"));
        String title = rs.getString("title");
        String lore = rs.getString("lore");
        String rewardData = rs.getString("reward_data");
        if (lore == null) lore = "";
        if (rewardData == null) rewardData = "";
        list.add(new QuestStage(
            rs.getInt("stage"),
            title,
            lore,
            goalType,
            rs.getInt("goal"),
            rewardType,
            rs.getDouble("reward"),
            rewardData));
      }
      return list;
    }
  }

  /** Insert or update quest stage. */
  public void upsert(QuestStage stage) throws SQLException {
    String sql =
        "INSERT INTO fishing_quest(stage,title,lore,goal_type,goal,reward_type,reward,reward_data) " +
            "VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE title=VALUES(title), lore=VALUES(lore), " +
            "goal_type=VALUES(goal_type), goal=VALUES(goal), reward_type=VALUES(reward_type), " +
            "reward=VALUES(reward), reward_data=VALUES(reward_data)";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setInt(1, stage.stage());
      ps.setString(2, stage.title());
      ps.setString(3, stage.lore());
      ps.setString(4, stage.goalType().name());
      ps.setInt(5, stage.goal());
      ps.setString(6, stage.rewardType().name());
      ps.setDouble(7, stage.reward());
      ps.setString(8, stage.rewardData());
      ps.executeUpdate();
    }
  }
}

