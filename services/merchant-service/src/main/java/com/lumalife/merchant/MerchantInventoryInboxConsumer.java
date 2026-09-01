package com.lumalife.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
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
  private final MerchantInventoryCommandHandler commandHandler;

  public MerchantInventoryInboxConsumer(JdbcTemplate jdbc, ObjectMapper mapper,
                                        MerchantInventoryCommandHandler commandHandler) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.commandHandler = commandHandler;
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
      try {
        if ("inventory.reserve.requested".equals(eventType)) {
          MerchantStore.ReservationRequest request = reservationRequest(event.path("payload"));
          MerchantStore.InventoryReservation reservation = commandHandler.reserve(request, "event-" + eventId);
          appendResult(orderId, eventId, eventType, "inventory.result.reserved", reservation, null);
        } else if ("inventory.confirm.requested".equals(eventType)) {
          MerchantStore.InventoryReservation reservation = commandHandler.confirm(orderId);
          appendResult(orderId, eventId, eventType, "inventory.result.confirmed", reservation, null);
        } else if ("inventory.release.requested".equals(eventType)) {
          MerchantStore.InventoryReservation reservation = commandHandler.release(orderId, "event-" + eventId);
          appendResult(orderId, eventId, eventType, "inventory.result.released", reservation, null);
        } else {
          throw new IllegalArgumentException("不支持的库存事件: " + eventType);
        }
      } catch (IllegalArgumentException | IllegalStateException businessError) {
        // Validation/stock conflicts are terminal for this command. Persist a
        // result event and ACK the delivery; transient DB/network failures are
        // still thrown by the outer catch and will be retried by RabbitMQ.
        appendResult(orderId, eventId, eventType, "inventory.result.failed", null, businessError.getMessage());
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

  private MerchantStore.ReservationRequest reservationRequest(JsonNode payload) {
    long orderId = payload.path("orderId").asLong(0);
    String expiresAt = payload.path("expiresAt").asText("");
    List<MerchantStore.ReservationItem> items = new ArrayList<>();
    for (JsonNode item : payload.path("items")) {
      items.add(new MerchantStore.ReservationItem(
        item.path("itemType").asText(""), item.path("itemId").asLong(0),
        item.path("quantity").asInt(0), item.path("expectedVersion").asLong(0)));
    }
    return new MerchantStore.ReservationRequest(orderId, Instant.parse(expiresAt), items);
  }

  private void appendResult(long orderId, String sourceEventId, String sourceEventType, String eventType,
                            MerchantStore.InventoryReservation reservation, String errorMessage) {
    try {
      LinkedHashMap<String, Object> result = new LinkedHashMap<>();
      result.put("orderId", orderId);
      result.put("reservationStatus", reservation == null ? "FAILED" : reservation.status());
      result.put("sourceEventId", sourceEventId);
      result.put("sourceEventType", sourceEventType);
      if (errorMessage != null && !errorMessage.isBlank()) result.put("error", errorMessage);
      result.put("occurredAt", Instant.now().toString());
      String payload = mapper.writeValueAsString(result);
      jdbc.update("INSERT INTO merchant_outbox_event(aggregate_type,aggregate_id,event_type,payload,status,occurred_at) VALUES (?,?,?,?, 'PENDING', ?)",
          "ORDER", orderId, eventType, payload, java.sql.Timestamp.from(Instant.now()));
    } catch (Exception error) {
      throw new IllegalStateException("库存结果事件序列化失败", error);
    }
  }
}
