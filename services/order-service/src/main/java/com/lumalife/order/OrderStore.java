package com.lumalife.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** Order-owned transactional slice. The user/merchant ids are references only. */
@Service
public class OrderStore {
  public record Order(long id, long userId, long merchantId, long productId, int quantity,
                      long totalCent, String status, Instant createdAt) {}

  private final AtomicLong ids = new AtomicLong(4000);
  private final Map<Long, Order> orders = new LinkedHashMap<>();

  public synchronized Order create(CreateOrderRequest request) {
    if (request.quantity() <= 0) throw new IllegalArgumentException("数量必须大于 0");
    Order order = new Order(ids.incrementAndGet(), request.userId(), request.merchantId(), request.productId(),
      request.quantity(), request.totalCent(), "PENDING_PAYMENT", Instant.now());
    orders.put(order.id(), order);
    return order;
  }

  public synchronized List<Order> byUser(long userId) {
    return orders.values().stream().filter(item -> item.userId() == userId).toList();
  }

  public synchronized Order cancel(long userId, long id) {
    Order order = orders.get(id);
    if (order == null || order.userId() != userId) throw new IllegalArgumentException("订单不存在");
    if (!"PENDING_PAYMENT".equals(order.status())) throw new IllegalStateException("当前状态不可取消");
    Order cancelled = new Order(order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), "CANCELLED", order.createdAt());
    orders.put(id, cancelled);
    return cancelled;
  }

  record CreateOrderRequest(long userId, long merchantId, long productId, int quantity, long totalCent) {}
}
