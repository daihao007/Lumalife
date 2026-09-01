package com.lumalife;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DatabaseAssetsTest {
  private static final Path SEED_FILE = Path.of("..", "database", "seeds", "demo-data.sql");
  private static final Path PAYMENT_CONTRACT_MIGRATION =
    Path.of("..", "database", "migrations", "V002__payment_idempotency_scope.sql");
  private static final Path BUSINESS_STATE_MIGRATION =
    Path.of("..", "database", "migrations", "V003__business_state_store.sql");
  private static final Path SERVICE_PAYMENT_KEY_MIGRATION =
    Path.of("..", "database", "migrations", "V007__service_payment_global_idempotency.sql");
  private static final Path SERVICE_ORDER_LINES_MIGRATION =
    Path.of("..", "database", "migrations", "V008__service_order_lines.sql");
  private static final Path MICROSERVICE_DURABILITY_MIGRATION =
    Path.of("..", "database", "migrations", "V009__microservice_durability_fixes.sql");
  private static final Path SERVICE_BACKFILL = Path.of("..", "database", "backfill-services.sql");
  private static final Path DATABASE_BOOTSTRAP =
    Path.of("..", "database", "init", "10-bootstrap.sh");

  @Test
  void demoSeedPasswordsUseSpringCompatibleBcryptHashes() throws IOException {
    String sql = Files.readString(SEED_FILE);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    assertThat(encoder.matches("abc123456", passwordHash(sql, "13800000001"))).isTrue();
    assertThat(encoder.matches("admin123456", passwordHash(sql, "13800000000"))).isTrue();
  }

  @Test
  void demoSeedKeepsIdsRequiredByTheMemoryBaseline() throws IOException {
    String sql = Files.readString(SEED_FILE);

    assertThat(sql).contains("(1001, 1, '藤椒鸡饭'");
    assertThat(sql).contains("(1007, 4, '栗子巴斯克'");
  }

  @Test
  void paymentMigrationEnforcesUserScopedIdempotencyAndProcessingState() throws IOException {
    String sql = Files.readString(PAYMENT_CONTRACT_MIGRATION);
    String serviceSql = Files.readString(SERVICE_PAYMENT_KEY_MIGRATION);
    String bootstrap = Files.readString(DATABASE_BOOTSTRAP);

    assertThat(sql).contains("UNIQUE KEY uk_payment_request (user_id, client_request_id)");
    assertThat(sql).contains("status IN ('PROCESSING', 'SUCCESS', 'FAILED')");
    assertThat(sql).doesNotContain("user_id, order_id, client_request_id");
    assertThat(serviceSql).contains("UNIQUE KEY uk_service_payment_request (user_id, client_request_id)");
    assertThat(bootstrap).contains("for migration in /database/migrations/V[0-9][0-9][0-9]__*.sql");
    assertThat(bootstrap).doesNotContain("schema_file=/database/migrations/V001__baseline_schema.sql");
  }

  @Test
  void businessStateMigrationPreservesLegacyImportSource() throws IOException {
    String sql = Files.readString(BUSINESS_STATE_MIGRATION);

    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS business_state");
    assertThat(sql).contains("payload JSON NOT NULL");
    assertThat(sql).contains("PRIMARY KEY (state_key)");
  }

  @Test
  void serviceOrderLinesPreserveMultiItemBackfill() throws IOException {
    String migration = Files.readString(SERVICE_ORDER_LINES_MIGRATION);
    String backfill = Files.readString(SERVICE_BACKFILL);

    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS service_order_line");
    assertThat(backfill).contains("INSERT INTO service_order_line");
    assertThat(backfill).contains("SUM(oi.quantity)");
    assertThat(backfill).contains("oi.item_name_snapshot");
  }

  @Test
  void durabilityMigrationExpandsAvatarAndProvidesOutboxFoundation() throws IOException {
    String migration = Files.readString(MICROSERVICE_DURABILITY_MIGRATION);

    assertThat(migration).contains("MEDIUMTEXT");
    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS service_outbox_event");
  }

  private String passwordHash(String sql, String phone) {
    Pattern accountRow = Pattern.compile("\\(\\d+, '" + Pattern.quote(phone) + "', '([^']+)'");
    Matcher matcher = accountRow.matcher(sql);
    assertThat(matcher.find()).as("seed account %s exists", phone).isTrue();
    return matcher.group(1);
  }
}
