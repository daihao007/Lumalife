package com.lumalife.merchant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes one inventory command in its own transaction. The Inbox transaction
 * can then persist a business failure result without committing a partial stock
 * update or poisoning the RabbitMQ delivery.
 */
@Component
public class MerchantInventoryCommandHandler {
  private final MerchantStore store;

  public MerchantInventoryCommandHandler(MerchantStore store) {
    this.store = store;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public MerchantStore.InventoryReservation reserve(MerchantStore.ReservationRequest request, String idempotencyKey) {
    return store.reserveInventory(request, idempotencyKey);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public MerchantStore.InventoryReservation confirm(long orderId) {
    return store.confirmInventory(orderId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public MerchantStore.InventoryReservation release(long orderId, String idempotencyKey) {
    return store.releaseInventory(orderId, idempotencyKey);
  }
}
