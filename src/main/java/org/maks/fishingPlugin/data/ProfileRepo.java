package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Repository for profile data. */
public class ProfileRepo {

  private final DataSource dataSource;

  public ProfileRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public Optional<Profile> find(UUID uuid) throws SQLException {
    String sql =
        "SELECT player_uuid, rod_level, rod_xp, total_catches, total_weight_g, largest_catch_g "
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
                  rs.getLong(6)));
        }
        return Optional.empty();
      }
    }
  }

  public void upsert(Profile profile) throws SQLException {
    String sql =
        "INSERT INTO fishing_profile(" +
            "player_uuid, rod_level, rod_xp, total_catches, total_weight_g, largest_catch_g) " +
            "VALUES(?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE rod_level=VALUES(rod_level), rod_xp=VALUES(rod_xp), " +
            "total_catches=VALUES(total_catches), total_weight_g=VALUES(total_weight_g), " +
            "largest_catch_g=VALUES(largest_catch_g)";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, profile.playerUuid().toString());
      ps.setInt(2, profile.rodLevel());
      ps.setLong(3, profile.rodXp());
      ps.setLong(4, profile.totalCatches());
      ps.setLong(5, profile.totalWeightG());
      ps.setLong(6, profile.largestCatchG());
      ps.executeUpdate();
    }
  }
}

