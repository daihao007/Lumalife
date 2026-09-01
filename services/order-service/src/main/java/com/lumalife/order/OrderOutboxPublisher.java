package com.lumalife.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Publishes order-owned outbox rows after the local transaction commits.
 * The sink is deliberately HTTP and optional: the course deployment can run
 * without a broker, while production can point it at an event gateway or an
 * Inbox endpoint without changing order business code.
 */
@Component
@ConditionalOnProperty(name = "lumalife.events.publisher.enabled", havingValue = "true")
public class OrderOutboxPublisher {
  private final JdbcTemplate jdbc;
  private final RestClient sink;
  private final ObjectMapper objectMapper;

  public OrderOutboxPublisher(ObjectProvider<JdbcTemplate> jdbcProvider,
                              RestClient.Builder restClientBuilder,
                              ObjectProvider<ObjectMapper> objectMapperProvider,
                              @Value("${lumalife.events.sink-url:}") String sinkUrl,
                              @Value("${lumalife.internal.service-token:}") String serviceToken) {
    this.jdbc = jdbcProvider.getIfAvailable();
    this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    String normalizedUrl = sinkUrl == null ? "" : sinkUrl.trim();
    this.sink = normalizedUrl.isBlank() ? null : restClientBuilder
      .baseUrl(normalizedUrl)
      .defaultHeader("X-Luma-Service-Token", serviceToken == null ? "" : serviceToken)
      .build();
  }

  @Scheduled(fixedDelayString = "${lumalife.events.publisher.fixed-delay-ms:5000}")
  public void publishPending() {
    if (jdbc == null || sink == null) return;
    List<OutboxEvent> events = jdbc.query(
      "SELECT id,aggregate_type,aggregate_id,event_type,payload,occurred_at "
        + "FROM service_outbox_event WHERE status IN ('PENDING','FAILED') ORDER BY id LIMIT 50",
      (rs, row) -> new OutboxEvent(rs.getLong("id"), rs.getString("aggregate_type"),
        rs.getLong("aggregate_id"), rs.getString("event_type"), rs.getString("payload"),
        rs.getTimestamp("occurred_at")));
    for (OutboxEvent event : events) publish(event);
  }

  private void publish(OutboxEvent event) {
    try {
      JsonNode payload = objectMapper.readTree(event.payload());
      sink.post().header("X-Luma-Event-Type", event.eventType())
        .body(new EventEnvelope(event.id(), event.aggregateType(), event.aggregateId(), event.eventType(), payload,
          event.occurredAt().toInstant().toString()))
        .retrieve().toBodilessEntity();
      jdbc.update("UPDATE service_outbox_event SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP "
          + "WHERE id=? AND status IN ('PENDING','FAILED')", event.id());
    } catch (Exception error) {
      jdbc.update("UPDATE service_outbox_event SET status='FAILED' WHERE id=? AND status IN ('PENDING','FAILED')", event.id());
    }
  }

  record OutboxEvent(long id, String aggregateType, long aggregateId, String eventType,
                     String payload, Timestamp occurredAt) {}

  record EventEnvelope(long id, String aggregateType, long aggregateId, String eventType,
                       JsonNode payload, String occurredAt) {}
}
