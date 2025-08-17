package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.maks.fishingPlugin.service.TreasureMapService.Lair;

/** Repository for lair locks ensuring single occupant per lair. */
public class LairLockRepo {
  public record Lock(Lair lair, UUID playerUuid, UUID mapId, long startedAt) {}

  private final DataSource dataSource;

  public LairLockRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Create table if absent. */
  public void init() throws SQLException {
    String sql = "CREATE TABLE IF NOT EXISTS fishing_lair_lock (" +
        "lair VARCHAR(16) PRIMARY KEY, " +
        "player_uuid VARCHAR(36) NOT NULL, " +
        "map_id VARCHAR(36) NOT NULL, " +
        "started_at BIGINT NOT NULL" +
        ")";
    try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  /** Attempt to lock a lair, returning false if already occupied. */
  public boolean tryLock(Lair lair, UUID player, UUID mapId) throws SQLException {
    String sql = "INSERT INTO fishing_lair_lock(lair,player_uuid,map_id,started_at) VALUES(?,?,?,?)";
    try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, lair.name());
      ps.setString(2, player.toString());
      ps.setString(3, mapId.toString());
      ps.setLong(4, System.currentTimeMillis());
      ps.executeUpdate();
      return true;
    } catch (SQLException e) {
      return false; // assume duplicate key
    }
  }

  /** Release a lair lock. */
  public void release(Lair lair) throws SQLException {
    String sql = "DELETE FROM fishing_lair_lock WHERE lair=?";
    try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, lair.name());
      ps.executeUpdate();
    }
  }

  /** Release all lair locks. */
  public void releaseAll() throws SQLException {
    String sql = "DELETE FROM fishing_lair_lock";
    try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  /** Remove locks older than cutoffMillis epoch. */
  public int cleanupOlderThan(long cutoffMillis) throws SQLException {
    String sql = "DELETE FROM fishing_lair_lock WHERE started_at < ?";
    try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setLong(1, cutoffMillis);
      return ps.executeUpdate();
    }
  }

  /** Load all current locks. */
  public List<Lock> findAll() throws SQLException {
    String sql = "SELECT lair, player_uuid, map_id, started_at FROM fishing_lair_lock";
    try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
      List<Lock> list = new ArrayList<>();
      while (rs.next()) {
        Lair lair = Lair.valueOf(rs.getString(1));
        UUID player = UUID.fromString(rs.getString(2));
        UUID map = UUID.fromString(rs.getString(3));
        long started = rs.getLong(4);
        list.add(new Lock(lair, player, map, started));
      }
      return list;
    }
  }
}
