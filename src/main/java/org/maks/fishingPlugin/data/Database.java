package org.maks.fishingPlugin.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Simple wrapper around HikariCP for obtaining a {@link DataSource}.
 */
public class Database {

  private final HikariDataSource dataSource;

  public Database(String jdbcUrl, String username, String password) throws SQLException {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    if (username != null) {
      config.setUsername(username);
    }
    if (password != null) {
      config.setPassword(password);
    }
    try {
      this.dataSource = new HikariDataSource(config);
    } catch (RuntimeException e) {
      throw new SQLException("Failed to initialize connection pool", e);
    }
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  public void close() {
    dataSource.close();
  }
}

