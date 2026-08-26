package com.lumalife.service;

import com.lumalife.domain.Models.CartItem;
import com.lumalife.domain.Models.CartLine;
import java.util.List;
import org.springframework.stereotype.Service;
import com.lumalife.service.boundary.OrderServicePort;

@Service
public class CartService {
  private final OrderServicePort order;

  public CartService(OrderServicePort order) {
    this.order = order;
  }

  public List<CartItem> cart(long userId) {
    return order.cart(userId);
  }

  public List<CartLine> cartDetail(long userId) {
    return order.cartDetail(userId);
  }

  public List<CartItem> addCart(long userId, long productId, int quantity) {
    return order.addCart(userId, productId, quantity);
  }

  public List<CartItem> updateCartItem(long userId, long productId, int quantity) {
    return order.updateCartItem(userId, productId, quantity);
  }

  public List<CartItem> removeCartItem(long userId, long productId) {
    return order.removeCartItem(userId, productId);
  }

  public void clearCart(long userId) {
    order.clearCart(userId);
  }
}
