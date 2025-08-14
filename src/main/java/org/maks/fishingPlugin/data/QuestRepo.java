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
}

