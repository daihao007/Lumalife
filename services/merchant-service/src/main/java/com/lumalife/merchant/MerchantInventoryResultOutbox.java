package com.lumalife.merchant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes one durable inventory result for each processed command or reconciliation. */
@Component
final class MerchantInventoryResultOutbox {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  @Autowired
  MerchantInventoryResultOutbox(ObjectProvider<JdbcTemplate> jdbcProvider,
                                ObjectProvider<ObjectMapper> mapperProvider) {
    this.jdbc = jdbcProvider.getIfAvailable();
    this.mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
  }

  MerchantInventoryResultOutbox(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper == null ? new ObjectMapper() : mapper;
  }

  void append(long orderId, String sourceEventId, String sourceEventType, String eventType,
              MerchantStore.InventoryReservation reservation, String errorMessage) {
    if (jdbc == null) return;
    try {
      Instant occurredAt = Instant.now();
      LinkedHashMap<String, Object> result = new LinkedHashMap<>();
      result.put("orderId", orderId);
      result.put("reservationStatus", reservation == null ? "FAILED" : reservation.status());
      result.put("sourceEventId", sourceEventId);
      result.put("sourceEventType", sourceEventType);
      result.put("outcome", outcome(eventType));
      if (errorMessage != null && !errorMessage.isBlank()) result.put("error", errorMessage);
      result.put("occurredAt", occurredAt.toString());
      String payload = mapper.writeValueAsString(result);
      String deduplicationKey = sourceEventId + ":" + eventType;
      jdbc.update("INSERT INTO merchant_outbox_event(aggregate_type,aggregate_id,event_type,payload,status,deduplication_key,occurred_at) "
          + "VALUES (?,?,?,?,'PENDING',?,?) ON DUPLICATE KEY UPDATE id=id",
          "ORDER", orderId, eventType, payload, deduplicationKey,
          java.sql.Timestamp.from(occurredAt));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("库存结果事件序列化失败", error);
    }
  }

  private String outcome(String eventType) {
    return switch (eventType) {
      case "inventory.result.released" -> "RELEASED";
      case "inventory.result.check_required" -> "CHECK_REQUIRED";
      case "inventory.result.release_failed" -> "RELEASE_FAILED";
      default -> "FAILED";
    };
  }
}
