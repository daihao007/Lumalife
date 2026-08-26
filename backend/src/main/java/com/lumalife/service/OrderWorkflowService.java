package com.lumalife.service;

import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.Review;
import com.lumalife.domain.Models.User;
import java.util.List;
import org.springframework.stereotype.Service;
import com.lumalife.service.boundary.OrderServicePort;

@Service
public class OrderWorkflowService {
  private final OrderServicePort order;

  public OrderWorkflowService(OrderServicePort order) {
    this.order = order;
  }

  public List<Order> createDeliveryOrders(User user, Long addressId) {
    return order.createDeliveryOrders(user, addressId);
  }

  public Order createGroupOrder(User user, long dealId, int quantity) {
    return order.createGroupOrder(user, dealId, quantity);
  }

  public Order pay(User user, long orderId, String clientRequestId) {
    return order.pay(user, orderId, clientRequestId);
  }

  public Order cancel(User user, long orderId) {
    return order.cancel(user, orderId);
  }

  public Order receive(User user, long orderId) {
    return order.receive(user, orderId);
  }

  public List<Order> userOrders(User user) {
    return order.userOrders(user);
  }

  public Review review(User user, long orderId, int score, int tasteScore, int serviceScore, String content) {
    return order.review(user, orderId, score, tasteScore, serviceScore, content);
  }
}
