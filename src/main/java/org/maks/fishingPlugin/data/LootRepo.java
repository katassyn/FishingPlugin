package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.LootEntry;

/** Repository providing loot entries. */
public class LootRepo {

  private final DataSource dataSource;

  public LootRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public List<LootEntry> findAll() throws SQLException {
    String sql = "SELECT key, category, base_weight, min_rod_level, broadcast, " +
        "price_base, price_per_kg, payout_multiplier, min_weight_g, max_weight_g, item_base64 FROM loot";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      List<LootEntry> list = new ArrayList<>();
      while (rs.next()) {
        list.add(new LootEntry(
            rs.getString(1),
            Category.valueOf(rs.getString(2)),
            rs.getDouble(3),
            rs.getInt(4),
            rs.getBoolean(5),
            rs.getDouble(6),
            rs.getDouble(7),
            rs.getDouble(8),
            rs.getDouble(9),
            rs.getDouble(10),
            rs.getString(11)
        ));
      }
      return list;
    }
  }
}

