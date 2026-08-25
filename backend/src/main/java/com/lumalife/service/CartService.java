package com.lumalife.service;

import com.lumalife.domain.Models.CartItem;
import com.lumalife.domain.Models.CartLine;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CartService {
  private final DemoStore store;

  public CartService(DemoStore store) {
    this.store = store;
  }

  public List<CartItem> cart(long userId) {
    return store.cart(userId);
  }

  public List<CartLine> cartDetail(long userId) {
    return store.cartDetail(userId);
  }

  public List<CartItem> addCart(long userId, long productId, int quantity) {
    return store.addCart(userId, productId, quantity);
  }

  public List<CartItem> updateCartItem(long userId, long productId, int quantity) {
    return store.updateCartItem(userId, productId, quantity);
  }

  public List<CartItem> removeCartItem(long userId, long productId) {
    return store.removeCartItem(userId, productId);
  }

  public void clearCart(long userId) {
    store.clearCart(userId);
  }
}
