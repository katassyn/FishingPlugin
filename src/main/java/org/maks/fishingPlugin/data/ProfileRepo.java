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
    String sql = "SELECT player_uuid, rod_level, rod_xp FROM fishing_profile WHERE player_uuid=?";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, uuid.toString());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(new Profile(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getLong(3)));
        }
        return Optional.empty();
      }
    }
  }

  public void upsert(Profile profile) throws SQLException {
    String sql =
        "INSERT INTO fishing_profile(player_uuid, rod_level, rod_xp) VALUES(?,?,?) " +
        "ON DUPLICATE KEY UPDATE rod_level=VALUES(rod_level), rod_xp=VALUES(rod_xp)";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, profile.playerUuid().toString());
      ps.setInt(2, profile.rodLevel());
      ps.setLong(3, profile.rodXp());
      ps.executeUpdate();
    }
  }
}

