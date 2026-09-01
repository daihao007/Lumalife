package com.lumalife.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes merchant-owned inventory results after the local transaction commits. */
@Component
@ConditionalOnProperty(name = "lumalife.events.broker.enabled", havingValue = "true")
public class RabbitMerchantOutboxPublisher {
  private final JdbcTemplate jdbc;
  private final RabbitTemplate rabbit;
  private final ObjectMapper mapper;
  private final String exchange;

  public RabbitMerchantOutboxPublisher(ObjectProvider<JdbcTemplate> jdbcProvider, RabbitTemplate rabbit,
      ObjectProvider<ObjectMapper> mapperProvider,
      @Value("${lumalife.events.broker.exchange:lumalife.events}") String exchange) {
    this.jdbc = jdbcProvider.getIfAvailable();
    this.rabbit = rabbit;
    this.mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
    this.exchange = exchange;
  }

  @Scheduled(fixedDelayString = "${lumalife.events.publisher.fixed-delay-ms:1000}")
  public void publishPending() {
    if (jdbc == null) return;
    List<OutboxEvent> events = jdbc.query(
        "SELECT id,aggregate_type,aggregate_id,event_type,payload,occurred_at FROM merchant_outbox_event "
            + "WHERE status IN ('PENDING','FAILED') ORDER BY id LIMIT 50",
        (rs, row) -> new OutboxEvent(rs.getLong("id"), rs.getString("aggregate_type"),
            rs.getLong("aggregate_id"), rs.getString("event_type"), rs.getString("payload"),
            rs.getTimestamp("occurred_at")));
    for (OutboxEvent event : events) publish(event);
  }

  private void publish(OutboxEvent event) {
    try {
      JsonNode payload = mapper.readTree(event.payload());
      String message = mapper.writeValueAsString(new EventEnvelope("merchant-outbox-" + event.id(),
          event.aggregateType(), event.aggregateId(), event.eventType(), payload,
          event.occurredAt().toInstant().toString()));
      rabbit.convertAndSend(exchange, event.eventType(), message);
      jdbc.update("UPDATE merchant_outbox_event SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP "
          + "WHERE id=? AND status IN ('PENDING','FAILED')", event.id());
    } catch (Exception error) {
      jdbc.update("UPDATE merchant_outbox_event SET status='FAILED' WHERE id=? AND status IN ('PENDING','FAILED')",
          event.id());
    }
  }

  record OutboxEvent(long id, String aggregateType, long aggregateId, String eventType, String payload,
                     Timestamp occurredAt) {}
  record EventEnvelope(String eventId, String aggregateType, long aggregateId, String eventType,
                       JsonNode payload, String occurredAt) {}
}
