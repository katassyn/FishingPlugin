package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

  public List<MirrorItem> findAll() throws SQLException {
    String sql = "SELECT key, category, broadcast, item_base64 FROM mirror_item";
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
}
