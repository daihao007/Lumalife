package com.lumalife.service;

import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.Review;
import com.lumalife.domain.Models.User;
import com.lumalife.service.boundary.OrderServicePort;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Order-service adapter for merchant and platform-facing order operations.
 * MerchantService owns product/catalog writes; it never owns these methods.
 */
@Service
public class OrderAdminService {
  private final OrderServicePort order;

  public OrderAdminService(OrderServicePort order) {
    this.order = order;
  }

  public List<Order> merchantOrders(User admin) {
    return order.merchantOrders(admin);
  }

  public List<Review> merchantReviews(User admin) {
    return order.merchantReviews(admin);
  }

  public Order transition(User admin, long orderId, OrderStatus next) {
    return order.transition(admin, orderId, next);
  }

  public Order verifyCoupon(User admin, String code) {
    return order.verifyCoupon(admin, code);
  }
}
