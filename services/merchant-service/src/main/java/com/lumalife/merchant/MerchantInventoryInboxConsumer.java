package com.lumalife.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent consumer for inventory Saga commands. Database transaction and broker ack are separate by design. */
@Component
@ConditionalOnProperty(name = "lumalife.events.broker.enabled", havingValue = "true")
public class MerchantInventoryInboxConsumer {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final MerchantStore store;

  public MerchantInventoryInboxConsumer(JdbcTemplate jdbc, ObjectMapper mapper, MerchantStore store) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.store = store;
  }

  @RabbitListener(queues = "${lumalife.events.broker.queue:merchant-inventory-events}")
  @Transactional
  public void consume(String message) {
    try {
      JsonNode event = mapper.readTree(message);
      String eventId = event.path("eventId").asText("");
      String eventType = event.path("eventType").asText("");
      long orderId = event.path("aggregateId").asLong(0);
      if (eventId.isBlank() || orderId <= 0) throw new IllegalArgumentException("库存事件缺少事件标识或订单号");
      int inserted = jdbc.update("INSERT IGNORE INTO merchant_inbox_event(event_id,aggregate_type,aggregate_id,event_type,payload,status,received_at) VALUES (?,?,?,?,?,'RECEIVED',?)",
          eventId, event.path("aggregateType").asText("ORDER"), orderId, eventType, event.path("payload").toString(), java.sql.Timestamp.from(Instant.now()));
      if (inserted == 0 && isProcessed(eventId)) return;
      if ("inventory.confirm.requested".equals(eventType)) {
        MerchantStore.InventoryReservation reservation = store.confirmInventory(orderId);
        appendResult(orderId, eventId, "inventory.result.confirmed", reservation);
      } else if ("inventory.release.requested".equals(eventType)) {
        MerchantStore.InventoryReservation reservation = store.releaseInventory(orderId, "event-" + eventId);
        appendResult(orderId, eventId, "inventory.result.released", reservation);
      } else {
        throw new IllegalArgumentException("不支持的库存事件: " + eventType);
      }
      jdbc.update("UPDATE merchant_inbox_event SET status='PROCESSED',processed_at=CURRENT_TIMESTAMP,last_error=NULL WHERE event_id=?", eventId);
    } catch (Exception error) {
      throw new IllegalStateException("库存事件处理失败", error);
    }
  }

  private boolean isProcessed(String eventId) {
    String status = jdbc.queryForObject("SELECT status FROM merchant_inbox_event WHERE event_id=?", String.class, eventId);
    return "PROCESSED".equals(status);
  }

  private void appendResult(long orderId, String sourceEventId, String eventType,
                            MerchantStore.InventoryReservation reservation) {
    try {
      String payload = mapper.writeValueAsString(java.util.Map.of(
          "orderId", orderId,
          "reservationStatus", reservation.status(),
          "sourceEventId", sourceEventId,
          "occurredAt", Instant.now().toString()));
      jdbc.update("INSERT INTO merchant_outbox_event(aggregate_type,aggregate_id,event_type,payload,status,occurred_at) VALUES (?,?,?,?, 'PENDING', ?)",
          "ORDER", orderId, eventType, payload, java.sql.Timestamp.from(Instant.now()));
    } catch (Exception error) {
      throw new IllegalStateException("库存结果事件序列化失败", error);
    }
  }
}
