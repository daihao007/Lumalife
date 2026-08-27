package com.lumalife.service.boundary;

import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.domain.Models.CartItem;
import com.lumalife.domain.Models.CartLine;
import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.Review;
import com.lumalife.domain.Models.User;
import java.util.List;

/** Order-owned capability boundary, including merchant-facing order operations. */
public interface OrderServicePort {
  List<CartItem> cart(long userId);

  List<CartLine> cartDetail(long userId);

  List<CartItem> addCart(long userId, long productId, int quantity);

  List<CartItem> updateCartItem(long userId, long productId, int quantity);

  List<CartItem> removeCartItem(long userId, long productId);

  void clearCart(long userId);

  List<Order> createDeliveryOrders(User user, Long addressId);

  Order createGroupOrder(User user, long dealId, int quantity);

  Order pay(User user, long orderId, String clientRequestId);

  Order cancel(User user, long orderId);

  Order receive(User user, long orderId);

  List<Order> userOrders(User user);

  Review review(User user, long orderId, int score, int tasteScore, int serviceScore, String content);

  List<Order> merchantOrders(User admin);

  List<Review> merchantReviews(User admin);

  Order transition(User admin, long orderId, OrderStatus next);

  Order verifyCoupon(User admin, String code);
}
