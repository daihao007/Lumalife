package com.lumalife.merchant;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles expired reservations while holding database row locks per batch. */
@Component
@ConditionalOnBean(JdbcTemplate.class)
class MerchantExpiredReservationProcessor {
  private static final String SOURCE_EVENT_TYPE = "inventory.expiration.reconciliation";

  private final MerchantStore store;
  private final MerchantOrderPaymentStateClient paymentStateClient;
  private final MerchantInventoryResultOutbox resultOutbox;
  private final int batchSize;

  MerchantExpiredReservationProcessor(
      MerchantStore store,
      MerchantOrderPaymentStateClient paymentStateClient,
      MerchantInventoryResultOutbox resultOutbox,
      @Value("${lumalife.events.expiry.batch-size:50}") int batchSize) {
    this.store = store;
    this.paymentStateClient = paymentStateClient;
    this.resultOutbox = resultOutbox;
    this.batchSize = Math.max(1, batchSize);
  }

  @Scheduled(fixedDelayString = "${lumalife.events.expiry.fixed-delay-ms:5000}")
  @Transactional
  public void processExpiredReservations() {
    List<Long> orderIds = store.expiredReservationOrderIds(batchSize);
    for (Long orderId : orderIds) {
      reconcile(orderId);
    }
  }

  private void reconcile(long orderId) {
    store.recordExpiryAttempt(orderId);
    String paymentState;
    try {
      paymentState = paymentStateClient.paymentState(orderId);
    } catch (RuntimeException dependencyError) {
      markForManualCheck(orderId, message("order-service 状态不可用", dependencyError));
      return;
    }

    if (!"FAILED".equals(paymentState) && !"CANCELLED".equals(paymentState)) {
      markForManualCheck(orderId, "订单支付状态不允许自动释放: "
          + (paymentState == null || paymentState.isBlank() ? "UNKNOWN" : paymentState));
      return;
    }

    // The selected row is already locked by expiredReservationOrderIds(). A
    // database error is intentionally allowed to roll back the whole batch so
    // the next scheduler run retries it; a partial release must not be hidden
    // behind a manually-created result.
    MerchantStore.InventoryReservation reservation =
        store.releaseInventory(orderId, "expiry-release-" + orderId);
    appendOutcome(orderId, resultEventType(reservation), reservation, null);
  }

  private void markForManualCheck(long orderId, String reason) {
    MerchantStore.InventoryReservation reservation = store.markCheckRequired(orderId, reason);
    appendOutcome(orderId, resultEventType(reservation), reservation, reason);
  }

  private String resultEventType(MerchantStore.InventoryReservation reservation) {
    return switch (reservation.status()) {
      case "RELEASED" -> "inventory.result.released";
      case "CHECK_REQUIRED" -> "inventory.result.check_required";
      default -> throw new IllegalStateException("过期库存返回未知状态: " + reservation.status());
    };
  }

  private void appendOutcome(long orderId, String eventType,
                            MerchantStore.InventoryReservation reservation, String error) {
    resultOutbox.append(orderId, "inventory-expiry-" + orderId, SOURCE_EVENT_TYPE,
        eventType, reservation, error);
  }

  private String message(String prefix, RuntimeException error) {
    String detail = error.getMessage();
    String result = detail == null || detail.isBlank() ? prefix : prefix + ": " + detail;
    return result.length() <= 900 ? result : result.substring(0, 900);
  }
}
