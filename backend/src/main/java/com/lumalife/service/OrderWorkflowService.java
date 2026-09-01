package com.lumalife.service;

import com.lumalife.common.BusinessException;
import com.lumalife.domain.Models.Address;
import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.Review;
import com.lumalife.domain.Models.User;
import java.util.List;
import org.springframework.stereotype.Service;
import com.lumalife.service.boundary.OrderServicePort;
import com.lumalife.service.boundary.IdentityServicePort;

@Service
public class OrderWorkflowService {
  private final OrderServicePort order;
  private final IdentityServicePort identity;

  public OrderWorkflowService(OrderServicePort order, IdentityServicePort identity) {
    this.order = order;
    this.identity = identity;
  }

  public List<Order> createDeliveryOrders(User user, Long addressId) {
    List<Address> addresses = identity.addresses(user);
    if (addresses.isEmpty()) throw new BusinessException(40900, "请先维护收货地址");
    Address address = addresses.stream()
      .filter(item -> addressId == null ? item.defaultAddress() : item.id() == addressId)
      .findFirst()
      .orElseThrow(() -> new BusinessException(40400, "收货地址不存在"));
    return order.createDeliveryOrdersWithAddress(user, address);
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
