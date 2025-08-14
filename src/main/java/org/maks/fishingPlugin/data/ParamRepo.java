package org.maks.fishingPlugin.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

/** Generic key-value parameter repository. */
public class ParamRepo {

  private final DataSource dataSource;

  public ParamRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public Map<String, String> findAll() throws SQLException {
    String sql = "SELECT key, value FROM param";
    Map<String, String> map = new HashMap<>();
    try (Connection con = dataSource.getConnection();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        map.put(rs.getString(1), rs.getString(2));
      }
    }
    return map;
  }
}

