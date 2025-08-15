package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Repository for profile data. */
public class ProfileRepo {

  private final DataSource dataSource;

  public ProfileRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Create backing table if it doesn't exist. */
  public void init() throws SQLException {
    String sql = "CREATE TABLE IF NOT EXISTS fishing_profile (" +
        "player_uuid VARCHAR(36) PRIMARY KEY, " +
        "rod_level INT NOT NULL, " +
        "rod_xp BIGINT NOT NULL, " +
        "total_catches BIGINT NOT NULL, " +
        "total_weight_g BIGINT NOT NULL, " +
        "largest_catch_g BIGINT NOT NULL, " +
        "qs_earned BIGINT NOT NULL, " +
        "last_qte_sample BLOB, " +
        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
        "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
        ")";
    try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  public Optional<Profile> find(UUID uuid) throws SQLException {
    String sql =
        "SELECT player_uuid, rod_level, rod_xp, total_catches, total_weight_g, "
            + "largest_catch_g, qs_earned, last_qte_sample, created_at, updated_at "
            + "FROM fishing_profile WHERE player_uuid=?";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, uuid.toString());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(
              new Profile(
                  UUID.fromString(rs.getString(1)),
                  rs.getInt(2),
                  rs.getLong(3),
                  rs.getLong(4),
                  rs.getLong(5),
                  rs.getLong(6),
                  rs.getLong(7),
                  rs.getBytes(8),
                  rs.getTimestamp(9).toInstant(),
                  rs.getTimestamp(10).toInstant()));
        }
        return Optional.empty();
      }
    }
  }

  public void upsert(Profile profile) throws SQLException {
    String sql =
        "INSERT INTO fishing_profile(" +
            "player_uuid, rod_level, rod_xp, total_catches, total_weight_g, largest_catch_g, " +
            "qs_earned, last_qte_sample, created_at, updated_at) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE rod_level=VALUES(rod_level), rod_xp=VALUES(rod_xp), " +
            "total_catches=VALUES(total_catches), total_weight_g=VALUES(total_weight_g), " +
            "largest_catch_g=VALUES(largest_catch_g), qs_earned=VALUES(qs_earned), " +
            "last_qte_sample=VALUES(last_qte_sample), updated_at=VALUES(updated_at)";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, profile.playerUuid().toString());
      ps.setInt(2, profile.rodLevel());
      ps.setLong(3, profile.rodXp());
      ps.setLong(4, profile.totalCatches());
      ps.setLong(5, profile.totalWeightG());
      ps.setLong(6, profile.largestCatchG());
      ps.setLong(7, profile.qsEarned());
      ps.setBytes(8, profile.lastQteSample());
      ps.setTimestamp(9, java.sql.Timestamp.from(profile.createdAt()));
      ps.setTimestamp(10, java.sql.Timestamp.from(profile.updatedAt()));
      ps.executeUpdate();
    }
  }
}

