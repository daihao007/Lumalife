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
  private static final Path ORDER_MAIN_PAYMENT_PROJECTION_MIGRATION =
    Path.of("..", "database", "migrations", "V010__order_main_payment_projection.sql");
  private static final Path ORDER_ADDRESS_SNAPSHOT_MIGRATION =
    Path.of("..", "database", "migrations", "V011__order_address_snapshot.sql");
  private static final Path INVENTORY_RESERVATION_MIGRATION =
    Path.of("..", "database", "migrations", "V012__inventory_reservation_saga.sql");
  private static final Path ORDER_MERCHANT_SNAPSHOT_MIGRATION =
    Path.of("..", "database", "migrations", "V013__order_merchant_name_snapshot.sql");
  private static final Path INVENTORY_SAGA_RESULT_MIGRATION =
    Path.of("..", "database", "migrations", "V015__inventory_saga_result_delivery.sql");
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

  @Test
  void canonicalOrderProjectionMigrationAddsPaymentIdempotencyColumns() throws IOException {
    String migration = Files.readString(ORDER_MAIN_PAYMENT_PROJECTION_MIGRATION);

    assertThat(migration).contains("ALTER TABLE order_main");
    assertThat(migration).contains("ADD COLUMN client_request_id");
    assertThat(migration).contains("ADD COLUMN coupon_code");
    assertThat(migration).contains("uq_order_main_client_request");
  }

  @Test
  void orderAddressSnapshotMigrationAndBackfillPreserveDeliveryDestination() throws IOException {
    String migration = Files.readString(ORDER_ADDRESS_SNAPSHOT_MIGRATION);
    String backfill = Files.readString(SERVICE_BACKFILL);

    assertThat(migration).contains("ALTER TABLE order_record");
    assertThat(migration).contains("ADD COLUMN address_snapshot");
    assertThat(backfill).contains("address_snapshot");
  }

  @Test
  void inventoryReservationMigrationKeepsStockOwnershipInMerchantService() throws IOException {
    String migration = Files.readString(INVENTORY_RESERVATION_MIGRATION);

    assertThat(migration).contains("ALTER TABLE merchant_catalog");
    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS inventory_reservation");
    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS inventory_reservation_item");
    assertThat(migration).contains("uk_inventory_reservation_idempotency");
    assertThat(migration).contains("CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'CHECK_REQUIRED'))");
  }

  @Test
  void orderMerchantSnapshotMigrationKeepsHistoricalDetailsReadable() throws IOException {
    String migration = Files.readString(ORDER_MERCHANT_SNAPSHOT_MIGRATION);
    String backfill = Files.readString(SERVICE_BACKFILL);

    assertThat(migration).contains("ALTER TABLE order_record");
    assertThat(migration).contains("ADD COLUMN merchant_name_snapshot");
    assertThat(backfill).contains("merchant_name_snapshot");
  }

  @Test
  void inventorySagaMigrationProvidesBothInboxAndOutboxResultStores() throws IOException {
    String migration = Files.readString(INVENTORY_SAGA_RESULT_MIGRATION);

    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS merchant_outbox_event");
    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS order_inbox_event");
    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS order_inventory_saga");
    assertThat(migration).contains("uk_order_inventory_saga_request");
  }

  private String passwordHash(String sql, String phone) {
    Pattern accountRow = Pattern.compile("\\(\\d+, '" + Pattern.quote(phone) + "', '([^']+)'");
    Matcher matcher = accountRow.matcher(sql);
    assertThat(matcher.find()).as("seed account %s exists", phone).isTrue();
    return matcher.group(1);
  }
}
