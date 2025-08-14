package org.maks.fishingPlugin.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Simple wrapper around HikariCP for obtaining a {@link DataSource}.
 */
public class Database {

  private final HikariDataSource dataSource;

  public Database(String jdbcUrl, String username, String password) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    if (username != null) {
      config.setUsername(username);
    }
    if (password != null) {
      config.setPassword(password);
    }
    this.dataSource = new HikariDataSource(config);
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  public void close() {
    dataSource.close();
  }
}

