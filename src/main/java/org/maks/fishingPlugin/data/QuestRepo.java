package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

  public List<QuestStage> findAll() throws SQLException {
    String sql = "SELECT stage, goal, reward FROM quest ORDER BY stage";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      List<QuestStage> list = new ArrayList<>();
      while (rs.next()) {
        list.add(new QuestStage(rs.getInt(1), rs.getInt(2), rs.getDouble(3)));
      }
      return list;
    }
  }

  /** Insert or update quest stage. */
  public void upsert(QuestStage stage) throws SQLException {
    String sql = "MERGE INTO quest KEY(stage) VALUES (?,?,?)";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setInt(1, stage.stage());
      ps.setInt(2, stage.goal());
      ps.setDouble(3, stage.reward());
      ps.executeUpdate();
    }
  }
}

