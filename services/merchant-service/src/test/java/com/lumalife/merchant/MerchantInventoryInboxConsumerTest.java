package com.lumalife.merchant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MerchantInventoryInboxConsumerTest {
  @Test
  void publishesCheckRequiredWhenReleaseCannotBeFullyRestored() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MerchantInventoryCommandHandler handler = mock(MerchantInventoryCommandHandler.class);
    MerchantInventoryResultOutbox resultOutbox = mock(MerchantInventoryResultOutbox.class);
    MerchantStore.InventoryReservation reservation = new MerchantStore.InventoryReservation(
        42L, "CHECK_REQUIRED", Instant.now().plusSeconds(30), List.of());
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    when(handler.release(42L, "event-release-42")).thenReturn(reservation);

    new MerchantInventoryInboxConsumer(jdbc, new ObjectMapper(), handler, resultOutbox)
        .consume(message("release-42", "inventory.release.requested", 42L));

    verify(resultOutbox).append(42L, "release-42", "inventory.release.requested",
        "inventory.result.check_required", reservation, null);
    verify(resultOutbox, never()).append(eq(42L), eq("release-42"),
        eq("inventory.release.requested"), eq("inventory.result.released"), any(), any());
  }

  @Test
  void duplicateReleaseDeliveryInvokesHandlerAndWritesResultOnlyOnce() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MerchantInventoryCommandHandler handler = mock(MerchantInventoryCommandHandler.class);
    MerchantInventoryResultOutbox resultOutbox = mock(MerchantInventoryResultOutbox.class);
    MerchantStore.InventoryReservation reservation = new MerchantStore.InventoryReservation(
        42L, "RELEASED", Instant.now().plusSeconds(30), List.of());
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 1, 0);
    when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenReturn("PROCESSED");
    when(handler.release(42L, "event-release-42")).thenReturn(reservation);

    MerchantInventoryInboxConsumer consumer = new MerchantInventoryInboxConsumer(
        jdbc, new ObjectMapper(), handler, resultOutbox);
    String message = message("release-42", "inventory.release.requested", 42L);
    consumer.consume(message);
    consumer.consume(message);

    verify(handler, times(1)).release(42L, "event-release-42");
    verify(resultOutbox, times(1)).append(42L, "release-42", "inventory.release.requested",
        "inventory.result.released", reservation, null);
  }

  private String message(String eventId, String eventType, long orderId) throws Exception {
    return new ObjectMapper().writeValueAsString(java.util.Map.of(
        "eventId", eventId, "aggregateType", "ORDER", "aggregateId", orderId,
        "eventType", eventType, "payload", java.util.Map.of("orderId", orderId)));
  }
}
