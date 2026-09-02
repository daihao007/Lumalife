package com.lumalife.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
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

  @Transactional
  public void scheduleRelease(long orderId, long actorId, String clientRequestId) {
    scheduleRelease(orderId, actorId, clientRequestId, "COMPENSATION_REQUIRED", null);
  }

  /**
   * Joins the inventory-result consumer transaction. The consumer already
   * updates this Saga row before scheduling release; starting a new
   * transaction here would wait for the outer transaction's row lock and can
   * roll back the whole compensation path.
   */
  @Transactional
  public void scheduleRelease(long orderId, long actorId, String clientRequestId,
                              String failureStatus, String errorMessage) {
    if (jdbc == null) return;
    Instant occurredAt = Instant.now();
    String lastError = failureStatus + (errorMessage == null || errorMessage.isBlank() ? "" : ": " + errorMessage);
    jdbc.update("INSERT INTO order_inventory_saga(order_id,user_id,client_request_id,status,last_error) VALUES (?,?,?, 'RELEASE_PENDING',?) "
        + "ON DUPLICATE KEY UPDATE status='RELEASE_PENDING',last_error=?,updated_at=CURRENT_TIMESTAMP(3)",
        orderId, actorId, clientRequestId, lastError, lastError);
    try {
      LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
      payload.put("orderId", orderId);
      payload.put("actorId", actorId);
      payload.put("status", "RELEASE_PENDING");
      payload.put("failureStatus", failureStatus);
      payload.put("error", errorMessage);
      payload.put("clientRequestId", clientRequestId);
      payload.put("occurredAt", occurredAt.toString());
      jdbc.update("INSERT INTO service_outbox_event(aggregate_type,aggregate_id,event_type,payload,status,occurred_at) VALUES (?,?,?,?, 'PENDING', ?)",
          "ORDER", orderId, "inventory.release.requested", mapper.writeValueAsString(payload),
          java.sql.Timestamp.from(occurredAt));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("库存补偿事件序列化失败", error);
    }
  }
}
