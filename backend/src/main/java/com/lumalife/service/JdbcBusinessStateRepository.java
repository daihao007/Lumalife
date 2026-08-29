package com.lumalife.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** MySQL adapter used by the production and container profiles. */
@Repository
@ConditionalOnProperty(name = "lumalife.persistence", havingValue = "mysql")
public class JdbcBusinessStateRepository implements BusinessStateRepository {
  private final String url;
  private final String username;
  private final String password;

  public JdbcBusinessStateRepository(
      @Value("${lumalife.mysql.url}") String url,
      @Value("${lumalife.mysql.username}") String username,
      @Value("${lumalife.mysql.password}") String password) {
    this.url = url;
    this.username = username;
    this.password = password;
  }

  @Override
  public Optional<String> load() {
    String sql = "SELECT payload FROM business_state WHERE state_key = ?";
    try (Connection connection = open()) {
      ensureTable(connection);
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, "primary");
        try (ResultSet result = statement.executeQuery()) {
          return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
        }
      }
    } catch (SQLException error) {
      throw new IllegalStateException("Failed to load LumaLife business state from MySQL", error);
    }
  }

  @Override
  public void save(String payload) {
    String sql = "INSERT INTO business_state(state_key, payload) VALUES (?, CAST(? AS JSON)) "
      + "ON DUPLICATE KEY UPDATE payload = VALUES(payload), updated_at = CURRENT_TIMESTAMP(3)";
    try (Connection connection = open()) {
      ensureTable(connection);
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, "primary");
        statement.setString(2, payload);
        statement.executeUpdate();
      }
    } catch (SQLException error) {
      throw new IllegalStateException("Failed to save LumaLife business state to MySQL", error);
    }
  }

  private Connection open() throws SQLException {
    return DriverManager.getConnection(url, username, password);
  }

  private void ensureTable(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      CREATE TABLE IF NOT EXISTS business_state (
        state_key VARCHAR(64) NOT NULL,
        payload JSON NOT NULL,
        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (state_key)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
      """)) {
      statement.execute();
    }
  }
}
