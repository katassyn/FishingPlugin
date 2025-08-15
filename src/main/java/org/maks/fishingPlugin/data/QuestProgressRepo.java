package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.maks.fishingPlugin.model.QuestProgress;

/** Repository for quest progress. */
public class QuestProgressRepo {

  private final DataSource dataSource;

  public QuestProgressRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public Optional<QuestProgress> find(UUID uuid) throws SQLException {
    String sql = "SELECT player_uuid, stage, count FROM fishing_quests_chain_progress WHERE player_uuid=?";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, uuid.toString());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(
              new QuestProgress(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3)));
        }
        return Optional.empty();
      }
    }
  }

  /** Insert or update player quest progress. */
  public void upsert(QuestProgress progress) throws SQLException {
    String sql =
        "INSERT INTO fishing_quests_chain_progress(player_uuid, stage, count) VALUES(?,?,?) " +
            "ON DUPLICATE KEY UPDATE stage=VALUES(stage), count=VALUES(count)";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, progress.playerUuid().toString());
      ps.setInt(2, progress.stage());
      ps.setInt(3, progress.count());
      ps.executeUpdate();
    }
  }
}
