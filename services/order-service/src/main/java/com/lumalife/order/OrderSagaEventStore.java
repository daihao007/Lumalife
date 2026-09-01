package com.lumalife.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists an inventory release command in a new transaction when payment
 * work has already failed. This prevents a rolled-back payment transaction
 * from losing the compensation command after stock was reserved.
 */
@Component
public class OrderSagaEventStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public OrderSagaEventStore(ObjectProvider<JdbcTemplate> jdbcProvider,
                             ObjectProvider<ObjectMapper> mapperProvider) {
    this.jdbc = jdbcProvider.getIfAvailable();
    this.mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void scheduleRelease(long orderId, long actorId, String clientRequestId) {
    if (jdbc == null) return;
    Instant occurredAt = Instant.now();
    jdbc.update("INSERT INTO order_inventory_saga(order_id,user_id,client_request_id,status,last_error) VALUES (?,?,?, 'RELEASE_PENDING',NULL) "
        + "ON DUPLICATE KEY UPDATE status='RELEASE_PENDING',last_error=NULL,updated_at=CURRENT_TIMESTAMP(3)",
        orderId, actorId, clientRequestId);
    try {
      String payload = mapper.writeValueAsString(Map.of(
          "orderId", orderId,
          "actorId", actorId,
          "status", "RELEASE_PENDING",
          "clientRequestId", clientRequestId,
          "occurredAt", occurredAt.toString()));
      jdbc.update("INSERT INTO service_outbox_event(aggregate_type,aggregate_id,event_type,payload,status,occurred_at) VALUES (?,?,?,?, 'PENDING', ?)",
          "ORDER", orderId, "inventory.release.requested", payload,
          java.sql.Timestamp.from(occurredAt));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("库存补偿事件序列化失败", error);
    }
  }
}
