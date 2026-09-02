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

      JsonNode payload = event.path("payload");
      String sourceEventType = payload.path("sourceEventType").asText("");
      String sagaStatus = sagaStatus(eventType, payload, sourceEventType);
      String errorMessage = event.path("payload").path("error").asText("");
      if (errorMessage.isBlank()) errorMessage = defaultError(sagaStatus);
      int updated = jdbc.update("UPDATE order_inventory_saga SET status=?,last_error=? WHERE order_id=? "
          + "AND status IN " + allowedPreviousStatuses(sagaStatus),
          sagaStatus, sagaStatus.endsWith("_FAILED") || "FAILED".equals(sagaStatus)
              ? errorMessage : ("CHECK_REQUIRED".equals(sagaStatus) ? errorMessage : null), orderId);
      if (updated != 1) {
        // A second delivery with a different broker message id, or a late
        // result from an older attempt, must not move a terminal outcome back
        // to RELEASED. The row still has to be ACKed after it is observed.
        String currentStatus = currentSagaStatus(orderId);
        if (currentStatus == null || currentStatus.isBlank()) {
          throw new IllegalStateException("订单库存 Saga 不存在: " + orderId);
        }
        markProcessed(eventId);
        return;
      }
      if ("RESERVED".equals(sagaStatus)) appendConfirmCommand(orderId);
      if ("RESERVE_FAILED".equals(sagaStatus)) {
        failPaidOrder(orderId);
      }
      if ("CONFIRM_FAILED".equals(sagaStatus)) {
        failPaidOrderAndScheduleRelease(orderId, errorMessage);
      }
      markProcessed(eventId);
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
      case "inventory.release.requested", "inventory.expiration.reconciliation" -> "RELEASE_FAILED";
      default -> "FAILED";
    };
  }

  private String sagaStatus(String eventType, JsonNode payload, String sourceEventType) {
    return switch (eventType) {
      case "inventory.result.reserved" -> "RESERVED";
      case "inventory.result.confirmed" -> "CONFIRMED";
      case "inventory.result.check_required" -> "CHECK_REQUIRED";
      case "inventory.result.release_failed" -> "RELEASE_FAILED";
      case "inventory.result.released" -> releaseStatus(payload);
      case "inventory.result.failed" -> failureStatus(sourceEventType);
      default -> throw new IllegalArgumentException("不支持的库存结果事件: " + eventType);
    };
  }

  private String releaseStatus(JsonNode payload) {
    String reservationStatus = payload.path("reservationStatus").asText("");
    String declaredOutcome = payload.path("outcome").asText("");
    if ("CHECK_REQUIRED".equals(reservationStatus) || "CHECK_REQUIRED".equals(declaredOutcome)) {
      return "CHECK_REQUIRED";
    }
    if ("RELEASED".equals(reservationStatus) || "RELEASED".equals(declaredOutcome)) {
      return "RELEASED";
    }
    return "RELEASE_FAILED";
  }

  private String defaultError(String sagaStatus) {
    return switch (sagaStatus) {
      case "CHECK_REQUIRED" -> "库存释放需要人工核对";
      case "RELEASE_FAILED" -> "库存释放失败";
      case "RESERVE_FAILED", "CONFIRM_FAILED", "FAILED" -> "库存事件失败";
      default -> "";
    };
  }

  private String allowedPreviousStatuses(String sagaStatus) {
    return switch (sagaStatus) {
      case "RESERVED", "RESERVE_FAILED" -> "('RESERVE_PENDING')";
      case "CONFIRMED", "CONFIRM_FAILED" -> "('CONFIRM_PENDING')";
      case "RELEASED", "CHECK_REQUIRED", "RELEASE_FAILED" -> "('RESERVED','RELEASE_PENDING')";
      default -> "('RESERVE_PENDING','RESERVED','CONFIRM_PENDING','CONFIRMED','RELEASE_PENDING')";
    };
  }

  private String currentSagaStatus(long orderId) {
    try {
      return jdbc.queryForObject("SELECT status FROM order_inventory_saga WHERE order_id=? FOR UPDATE",
          String.class, orderId);
    } catch (RuntimeException error) {
      throw new IllegalStateException("订单库存 Saga 状态读取失败: " + orderId, error);
    }
  }

  private void markProcessed(String eventId) {
    jdbc.update("UPDATE order_inbox_event SET status='PROCESSED',processed_at=CURRENT_TIMESTAMP,last_error=NULL WHERE event_id=?", eventId);
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
