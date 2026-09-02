package com.lumalife.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
  private final OrderSagaEventStore sagaEventStore;

  public OrderInventoryResultConsumer(JdbcTemplate jdbc, ObjectMapper mapper,
                                     OrderSagaEventStore sagaEventStore) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.sagaEventStore = sagaEventStore;
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

      String sourceEventType = event.path("payload").path("sourceEventType").asText("");
      String sagaStatus = switch (eventType) {
        case "inventory.result.reserved" -> "RESERVED";
        case "inventory.result.confirmed" -> "CONFIRMED";
        case "inventory.result.released" -> "RELEASED";
        case "inventory.result.failed" -> failureStatus(sourceEventType);
        default -> throw new IllegalArgumentException("不支持的库存结果事件: " + eventType);
      };
      String errorMessage = event.path("payload").path("error").asText("");
      int updated = jdbc.update("UPDATE order_inventory_saga SET status=?,last_error=? WHERE order_id=?",
          sagaStatus, sagaStatus.endsWith("_FAILED") || "FAILED".equals(sagaStatus)
              ? (errorMessage.isBlank() ? "库存事件失败" : errorMessage) : null, orderId);
      if (updated != 1) throw new IllegalStateException("订单库存 Saga 不存在: " + orderId);
      if ("RESERVED".equals(sagaStatus)) appendConfirmCommand(orderId);
      if ("RESERVE_FAILED".equals(sagaStatus)) {
        failPaidOrder(orderId);
      }
      if ("CONFIRM_FAILED".equals(sagaStatus)) {
        failPaidOrderAndScheduleRelease(orderId, errorMessage);
      }
      jdbc.update("UPDATE order_inbox_event SET status='PROCESSED',processed_at=CURRENT_TIMESTAMP,last_error=NULL WHERE event_id=?", eventId);
    } catch (Exception error) {
      throw new IllegalStateException("库存结果事件处理失败", error);
    }
  }

  private boolean isProcessed(String eventId) {
    String status = jdbc.queryForObject("SELECT status FROM order_inbox_event WHERE event_id=?", String.class, eventId);
    return "PROCESSED".equals(status);
  }

  private String failureStatus(String sourceEventType) {
    return switch (sourceEventType) {
      case "inventory.reserve.requested" -> "RESERVE_FAILED";
      case "inventory.confirm.requested" -> "CONFIRM_FAILED";
      default -> "FAILED";
    };
  }

  private void appendConfirmCommand(long orderId) {
    Map<String, Object> saga = jdbc.queryForMap(
        "SELECT user_id,client_request_id FROM order_inventory_saga WHERE order_id=?", orderId);
    long actorId = ((Number) saga.get("user_id")).longValue();
    String clientRequestId = String.valueOf(saga.get("client_request_id"));
    try {
      LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
      payload.put("orderId", orderId);
      payload.put("actorId", actorId);
      payload.put("status", "CONFIRM_PENDING");
      payload.put("clientRequestId", clientRequestId);
      payload.put("occurredAt", Instant.now().toString());
      jdbc.update("INSERT INTO service_outbox_event(aggregate_type,aggregate_id,event_type,payload,status,occurred_at) VALUES (?,?,?,?, 'PENDING', ?)",
          "ORDER", orderId, "inventory.confirm.requested", mapper.writeValueAsString(payload),
          java.sql.Timestamp.from(Instant.now()));
      jdbc.update("UPDATE order_inventory_saga SET status='CONFIRM_PENDING',last_error=NULL,updated_at=CURRENT_TIMESTAMP(3) WHERE order_id=?",
          orderId);
    } catch (Exception error) {
      throw new IllegalStateException("库存确认事件序列化失败", error);
    }
  }

  private void failPaidOrder(long orderId) {
    Map<String, Object> saga = jdbc.queryForMap(
        "SELECT user_id FROM order_inventory_saga WHERE order_id=?", orderId);
    long actorId = ((Number) saga.get("user_id")).longValue();
    int changed = jdbc.update("UPDATE order_record SET status='CANCELLED',version=version+1 WHERE id=? AND status='PAID'", orderId);
    jdbc.update("UPDATE service_payment SET status='FAILED',paid_at=NULL WHERE order_id=? AND status='SUCCESS'", orderId);
    jdbc.update("UPDATE service_coupon SET status='EXPIRED' WHERE order_id=? AND status='UNUSED'", orderId);
    if (changed == 1) {
      Integer nextVersion = jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM service_order_event WHERE order_id=?",
          Integer.class, orderId);
      Instant occurredAt = Instant.now();
      jdbc.update("INSERT INTO service_order_event(order_id,version,status,actor_id,occurred_at) VALUES (?,?,?,?,?)",
          orderId, nextVersion == null ? 1 : nextVersion, "CANCELLED", actorId,
          java.sql.Timestamp.from(occurredAt));
      try {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("actorId", actorId);
        payload.put("status", "CANCELLED");
        payload.put("occurredAt", occurredAt.toString());
        jdbc.update("INSERT INTO service_outbox_event(aggregate_type,aggregate_id,event_type,payload,status,occurred_at) VALUES (?,?,?,?, 'PENDING', ?)",
            "ORDER", orderId, "order.status.changed", mapper.writeValueAsString(payload),
            java.sql.Timestamp.from(occurredAt));
      } catch (Exception error) {
        throw new IllegalStateException("订单失败事件序列化失败", error);
      }
    }
  }

  private void failPaidOrderAndScheduleRelease(long orderId, String errorMessage) {
    Map<String, Object> saga = jdbc.queryForMap(
        "SELECT user_id,client_request_id FROM order_inventory_saga WHERE order_id=?", orderId);
    long actorId = ((Number) saga.get("user_id")).longValue();
    String clientRequestId = String.valueOf(saga.get("client_request_id"));
    failPaidOrder(orderId);
    sagaEventStore.scheduleRelease(orderId, actorId, clientRequestId, "CONFIRM_FAILED", errorMessage);
  }
}
