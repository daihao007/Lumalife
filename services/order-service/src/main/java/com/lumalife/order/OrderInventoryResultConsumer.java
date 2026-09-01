package com.lumalife.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent order-side Inbox for merchant inventory Saga results. */
@Component
@ConditionalOnProperty(name = "lumalife.events.broker.enabled", havingValue = "true")
public class OrderInventoryResultConsumer {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public OrderInventoryResultConsumer(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @RabbitListener(queues = "${lumalife.events.broker.result-queue:order-inventory-results}")
  @Transactional
  public void consume(String message) {
    try {
      JsonNode event = mapper.readTree(message);
      String eventId = event.path("eventId").asText("");
      String eventType = event.path("eventType").asText("");
      long orderId = event.path("aggregateId").asLong(0);
      if (eventId.isBlank() || orderId <= 0) {
        throw new IllegalArgumentException("库存结果事件缺少事件标识或订单号");
      }
      int inserted = jdbc.update("INSERT IGNORE INTO order_inbox_event(event_id,aggregate_type,aggregate_id,event_type,payload,status,received_at) VALUES (?,?,?,?,?,'RECEIVED',?)",
          eventId, event.path("aggregateType").asText("ORDER"), orderId, eventType,
          event.path("payload").toString(), java.sql.Timestamp.from(Instant.now()));
      if (inserted == 0 && isProcessed(eventId)) return;

      String sagaStatus = switch (eventType) {
        case "inventory.result.confirmed" -> "CONFIRMED";
        case "inventory.result.released" -> "RELEASED";
        case "inventory.result.failed" -> "FAILED";
        default -> throw new IllegalArgumentException("不支持的库存结果事件: " + eventType);
      };
      int updated = jdbc.update("UPDATE order_inventory_saga SET status=?,last_error=? WHERE order_id=?",
          sagaStatus, "FAILED".equals(sagaStatus) ? event.path("payload").path("error").asText("库存事件失败") : null, orderId);
      if (updated != 1) throw new IllegalStateException("订单库存 Saga 不存在: " + orderId);
      jdbc.update("UPDATE order_inbox_event SET status='PROCESSED',processed_at=CURRENT_TIMESTAMP,last_error=NULL WHERE event_id=?", eventId);
    } catch (Exception error) {
      throw new IllegalStateException("库存结果事件处理失败", error);
    }
  }

  private boolean isProcessed(String eventId) {
    String status = jdbc.queryForObject("SELECT status FROM order_inbox_event WHERE event_id=?", String.class, eventId);
    return "PROCESSED".equals(status);
  }
}
