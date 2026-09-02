package com.lumalife.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OrderStoreConsistencyTest {
  @Test
  void remoteOrderReadsMustObserveAnAsyncDatabaseStatusChange() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OrderStore.Order paid = new OrderStore.Order(42L, 7L, 1L, 1001L, 1, 2680,
        "PAID", Instant.parse("2026-09-02T00:00:00Z"));
    OrderStore.Order cancelled = new OrderStore.Order(42L, 7L, 1L, 1001L, 1, 2680,
        "CANCELLED", Instant.parse("2026-09-02T00:00:00Z"));
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(paid), List.of(cancelled));

    OrderStore store = new OrderStore(jdbc);
    try {
      var cacheField = OrderStore.class.getDeclaredField("orders");
      cacheField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<Long, OrderStore.Order> cache = (Map<Long, OrderStore.Order>) cacheField.get(store);
      cache.put(42L, paid);
    } catch (ReflectiveOperationException error) {
      throw new AssertionError("无法准备支付后的订单缓存", error);
    }

    assertThat(store.order(42L).status()).isEqualTo("PAID");
    assertThat(store.order(42L).status()).isEqualTo("CANCELLED");
  }

  @Test
  void remoteStatusTransitionMustFailWhenTheConditionalUpdateTouchesNoRows() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OrderStore.Order paid = new OrderStore.Order(42L, 7L, 1L, 1001L, 1, 2680,
        "PAID", Instant.parse("2026-09-02T00:00:00Z"));
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(paid));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

    OrderStore store = new OrderStore(jdbc);

    assertThatThrownBy(() -> store.transition(1L, 42L, "ACCEPTED"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("订单状态已变化，状态流转未完成");
  }
}
