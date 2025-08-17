package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.maks.fishingPlugin.service.TreasureMapService.MapState;
import org.maks.fishingPlugin.service.TreasureMapService.Lair;

/** Repository for persisting treasure map states. */
public class TreasureMapRepo {
  private final DataSource dataSource;

  public TreasureMapRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Create backing table if absent. */
  public void init() throws SQLException {
    String sql = "CREATE TABLE IF NOT EXISTS fishing_treasure_map (" +
        "map_id VARCHAR(36) PRIMARY KEY, " +
        "state VARCHAR(16) NOT NULL, " +
        "lair VARCHAR(16) NULL, " +
        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
        ")";
    try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  /** Insert or update a map entry. */
  public void upsert(UUID id, MapState state, Lair lair) throws SQLException {
    String sql = "INSERT INTO fishing_treasure_map(map_id,state,lair) VALUES(?,?,?) " +
        "ON DUPLICATE KEY UPDATE state=VALUES(state), lair=VALUES(lair)";
    try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, id.toString());
      ps.setString(2, state.name());
      if (lair != null) {
        ps.setString(3, lair.name());
      } else {
        ps.setNull(3, java.sql.Types.VARCHAR);
      }
      ps.executeUpdate();
    }
  }
}
