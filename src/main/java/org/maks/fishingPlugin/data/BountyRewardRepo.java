package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.maks.fishingPlugin.model.BountyReward;
import org.maks.fishingPlugin.service.TreasureMapService;

/** Repository for bounty rewards per lair. */
public class BountyRewardRepo {

  private final DataSource dataSource;

  public BountyRewardRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Create backing table if missing. */
  public void init() throws SQLException {
    String sql = "CREATE TABLE IF NOT EXISTS fishing_bounty_reward (" +
        "lair VARCHAR(16) PRIMARY KEY, " +
        "reward TEXT NOT NULL" +
        ")";
    try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  public List<BountyReward> findAll() throws SQLException {
    String sql = "SELECT lair, reward FROM fishing_bounty_reward";
    List<BountyReward> list = new ArrayList<>();
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        TreasureMapService.Lair lair = TreasureMapService.Lair.valueOf(rs.getString(1));
        String data = rs.getString(2);
        list.add(new BountyReward(lair, data));
      }
    }
    return list;
  }

  /** Insert or update reward for a lair. */
  public void upsert(BountyReward reward) throws SQLException {
    String sql = "INSERT INTO fishing_bounty_reward(lair,reward) VALUES(?,?) " +
        "ON DUPLICATE KEY UPDATE reward=VALUES(reward)";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, reward.lair().name());
      ps.setString(2, reward.rewardData());
      ps.executeUpdate();
    }
  }
}
