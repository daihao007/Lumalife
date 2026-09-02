package com.lumalife.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class OrderInventoryResultConsumerTest {
  @Test
  void confirmFailureCancelsPaymentAndSchedulesInventoryRelease() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OrderSagaEventStore sagaEventStore = mock(OrderSagaEventStore.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbc.queryForMap(anyString(), any(Object[].class)))
        .thenReturn(Map.of("user_id", 7L, "client_request_id", "pay-42"));
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(2);

    String message = new ObjectMapper().writeValueAsString(Map.of(
        "eventId", "inventory-result-42",
        "aggregateType", "ORDER",
        "aggregateId", 42,
        "eventType", "inventory.result.failed",
        "payload", Map.of(
            "sourceEventType", "inventory.confirm.requested",
            "error", "库存版本不一致")));

    new OrderInventoryResultConsumer(jdbc, new ObjectMapper(), sagaEventStore).consume(message);

    verify(jdbc).update(contains("UPDATE order_inventory_saga SET status=?"),
        eq("CONFIRM_FAILED"), eq("库存版本不一致"), eq(42L));
    verify(jdbc).update(contains("UPDATE order_record SET status='CANCELLED'"), eq(42L));
    verify(jdbc).update(contains("UPDATE service_payment SET status='FAILED'"), eq(42L));
    verify(sagaEventStore).scheduleRelease(42L, 7L, "pay-42", "CONFIRM_FAILED", "库存版本不一致");
    verify(jdbc).update(contains("UPDATE order_inbox_event SET status='PROCESSED'"),
        eq("inventory-result-42"));
  }

  @Test
  void releaseSchedulingJoinsTheConsumerTransaction() throws Exception {
    Transactional transaction = OrderSagaEventStore.class
        .getMethod("scheduleRelease", long.class, long.class, String.class, String.class, String.class)
        .getAnnotation(Transactional.class);

    Assertions.assertThat(transaction).isNotNull();
    Assertions.assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
  }
}
