package com.lumalife.service;

import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.Review;
import com.lumalife.domain.Models.User;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderWorkflowService {
  private final DemoStore store;

  public OrderWorkflowService(DemoStore store) {
    this.store = store;
  }

  public List<Order> createDeliveryOrders(User user, Long addressId) {
    return store.createDeliveryOrders(user, addressId);
  }

  public Order createGroupOrder(User user, long dealId, int quantity) {
    return store.createGroupOrder(user, dealId, quantity);
  }

  public Order pay(User user, long orderId, String clientRequestId) {
    return store.pay(user, orderId, clientRequestId);
  }

  public Order cancel(User user, long orderId) {
    return store.cancel(user, orderId);
  }

  public Order receive(User user, long orderId) {
    return store.receive(user, orderId);
  }

  public List<Order> userOrders(User user) {
    return store.userOrders(user);
  }

  public Review review(User user, long orderId, int score, int tasteScore, int serviceScore, String content) {
    return store.review(user, orderId, score, tasteScore, serviceScore, content);
  }
}
