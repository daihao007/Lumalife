package com.lumalife.merchant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MerchantExpiredReservationProcessorTest {
  @Test
  void failedPaymentAllowsNormalExpiredReservationRelease() {
    MerchantStore store = mock(MerchantStore.class);
    MerchantOrderPaymentStateClient paymentStateClient = mock(MerchantOrderPaymentStateClient.class);
    MerchantInventoryResultOutbox resultOutbox = mock(MerchantInventoryResultOutbox.class);
    MerchantStore.InventoryReservation released = reservation(42L, "RELEASED");
    when(store.expiredReservationOrderIds(50)).thenReturn(List.of(42L));
    when(paymentStateClient.paymentState(42L)).thenReturn("FAILED");
    when(store.releaseInventory(42L, "expiry-release-42")).thenReturn(released);

    new MerchantExpiredReservationProcessor(store, paymentStateClient, resultOutbox, 50)
        .processExpiredReservations();

    verify(store).releaseInventory(42L, "expiry-release-42");
    verify(resultOutbox).append(42L, "inventory-expiry-42",
        "inventory.expiration.reconciliation", "inventory.result.released", released, null);
  }

  @Test
  void successfulPaymentIsNeverAutomaticallyReleased() {
    MerchantStore store = mock(MerchantStore.class);
    MerchantOrderPaymentStateClient paymentStateClient = mock(MerchantOrderPaymentStateClient.class);
    MerchantInventoryResultOutbox resultOutbox = mock(MerchantInventoryResultOutbox.class);
    MerchantStore.InventoryReservation checked = reservation(42L, "CHECK_REQUIRED");
    when(store.expiredReservationOrderIds(50)).thenReturn(List.of(42L));
    when(paymentStateClient.paymentState(42L)).thenReturn("SUCCESS");
    when(store.markCheckRequired(eq(42L), anyString())).thenReturn(checked);

    new MerchantExpiredReservationProcessor(store, paymentStateClient, resultOutbox, 50)
        .processExpiredReservations();

    verify(store, never()).releaseInventory(42L, "expiry-release-42");
    verify(resultOutbox).append(eq(42L), eq("inventory-expiry-42"),
        eq("inventory.expiration.reconciliation"), eq("inventory.result.check_required"), eq(checked),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void unavailableOrderServiceMovesReservationToManualCheck() {
    MerchantStore store = mock(MerchantStore.class);
    MerchantOrderPaymentStateClient paymentStateClient = mock(MerchantOrderPaymentStateClient.class);
    MerchantInventoryResultOutbox resultOutbox = mock(MerchantInventoryResultOutbox.class);
    MerchantStore.InventoryReservation checked = reservation(42L, "CHECK_REQUIRED");
    when(store.expiredReservationOrderIds(50)).thenReturn(List.of(42L));
    when(paymentStateClient.paymentState(42L)).thenThrow(new IllegalStateException("order-service 暂时不可用"));
    when(store.markCheckRequired(eq(42L), anyString())).thenReturn(checked);

    new MerchantExpiredReservationProcessor(store, paymentStateClient, resultOutbox, 50)
        .processExpiredReservations();

    verify(store, never()).releaseInventory(42L, "expiry-release-42");
    verify(resultOutbox).append(eq(42L), eq("inventory-expiry-42"),
        eq("inventory.expiration.reconciliation"), eq("inventory.result.check_required"), eq(checked),
        org.mockito.ArgumentMatchers.anyString());
  }

  private MerchantStore.InventoryReservation reservation(long orderId, String status) {
    return new MerchantStore.InventoryReservation(orderId, status, Instant.now().minusSeconds(1), List.of());
  }
}
