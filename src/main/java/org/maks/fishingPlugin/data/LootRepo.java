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
        "price_base, price_per_kg, payout_multiplier, quality_s_weight, quality_a_weight, " +
        "quality_b_weight, quality_c_weight, min_weight_g, max_weight_g, item_base64 FROM fishing_item_def";
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
            rs.getDouble(11),
            rs.getDouble(12),
            rs.getDouble(13),
            rs.getDouble(14),
            rs.getString(15)
        ));
      }
      return list;
    }
  }

  /** Insert or update a loot entry. */
  public void upsert(LootEntry entry) throws SQLException {
    String sql =
        "INSERT INTO fishing_item_def(key,category,base_weight,min_rod_level,broadcast,price_base," +
            "price_per_kg,payout_multiplier,quality_s_weight,quality_a_weight,quality_b_weight,quality_c_weight," +
            "min_weight_g,max_weight_g,item_base64) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE category=VALUES(category), base_weight=VALUES(base_weight), " +
            "min_rod_level=VALUES(min_rod_level), broadcast=VALUES(broadcast), price_base=VALUES(price_base), " +
            "price_per_kg=VALUES(price_per_kg), payout_multiplier=VALUES(payout_multiplier), " +
            "quality_s_weight=VALUES(quality_s_weight), quality_a_weight=VALUES(quality_a_weight), " +
            "quality_b_weight=VALUES(quality_b_weight), quality_c_weight=VALUES(quality_c_weight), " +
            "min_weight_g=VALUES(min_weight_g), max_weight_g=VALUES(max_weight_g), item_base64=VALUES(item_base64)";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, entry.key());
      ps.setString(2, entry.category().name());
      ps.setDouble(3, entry.baseWeight());
      ps.setInt(4, entry.minRodLevel());
      ps.setBoolean(5, entry.broadcast());
      ps.setDouble(6, entry.priceBase());
      ps.setDouble(7, entry.pricePerKg());
      ps.setDouble(8, entry.payoutMultiplier());
      ps.setDouble(9, entry.qualitySWeight());
      ps.setDouble(10, entry.qualityAWeight());
      ps.setDouble(11, entry.qualityBWeight());
      ps.setDouble(12, entry.qualityCWeight());
      ps.setDouble(13, entry.minWeightG());
      ps.setDouble(14, entry.maxWeightG());
      ps.setString(15, entry.itemBase64());
      ps.executeUpdate();
    }
  }
}

