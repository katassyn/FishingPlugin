package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.maks.fishingPlugin.model.Category;
import org.maks.fishingPlugin.model.MirrorItem;

/** Repository providing mirror items. */
public class MirrorItemRepo {

  private final DataSource dataSource;

  public MirrorItemRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Create backing table if it doesn't exist. */
  public void init() throws SQLException {
    String sql = "CREATE TABLE IF NOT EXISTS fishing_mirror_item (" +
        "`key` VARCHAR(64) PRIMARY KEY, " +
        "category VARCHAR(32) NOT NULL, " +
        "broadcast BOOLEAN NOT NULL, " +
        "item_base64 TEXT NOT NULL" +
        ")";
    try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  public List<MirrorItem> findAll() throws SQLException {
    String sql = "SELECT `key`, category, broadcast, item_base64 FROM fishing_mirror_item";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      List<MirrorItem> list = new ArrayList<>();
      while (rs.next()) {
        list.add(new MirrorItem(
            rs.getString(1),
            Category.valueOf(rs.getString(2)),
            rs.getBoolean(3),
            rs.getString(4)
        ));
      }
      return list;
    }
  }

  /** Insert or update a mirror item definition. */
  public void upsert(MirrorItem item) throws SQLException {
    String sql =
        "INSERT INTO fishing_mirror_item(`key`,category,broadcast,item_base64) VALUES (?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE category=VALUES(category), broadcast=VALUES(broadcast), " +
            "item_base64=VALUES(item_base64)";
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, item.key());
      ps.setString(2, item.category().name());
      ps.setBoolean(3, item.broadcast());
      ps.setString(4, item.itemBase64());
      ps.executeUpdate();
    }
  }
}
