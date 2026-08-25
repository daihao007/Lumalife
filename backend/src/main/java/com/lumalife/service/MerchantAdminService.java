package com.lumalife.service;

import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.domain.Models.GroupDeal;
import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.Product;
import com.lumalife.domain.Models.Review;
import com.lumalife.domain.Models.User;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MerchantAdminService {
  private final DemoStore store;

  public MerchantAdminService(DemoStore store) {
    this.store = store;
  }

  public List<Order> merchantOrders(User admin) {
    return store.merchantOrders(admin);
  }

  public Map<String, Object> merchantProfile(User admin) {
    return store.merchantProfile(admin);
  }

  public Map<String, Object> updateMerchantNickname(User admin, String nickname) {
    return store.updateMerchantNickname(admin, nickname);
  }

  public List<Review> merchantReviews(User admin) {
    return store.merchantReviews(admin);
  }

  public List<Product> merchantProducts(User admin) {
    return store.merchantProducts(admin);
  }

  public Product saveProduct(User admin, Long id, String name, String description, long priceCent, int stock, boolean listed) {
    return store.saveProduct(admin, id, name, description, priceCent, stock, listed);
  }

  public Product toggleProduct(User admin, long id) {
    return store.toggleProduct(admin, id);
  }

  public void deleteProduct(User admin, long id) {
    store.deleteProduct(admin, id);
  }

  public List<GroupDeal> merchantDeals(User admin) {
    return store.merchantDeals(admin);
  }

  public GroupDeal saveDeal(User admin, Long id, String title, String description, long priceCent, int stock, boolean active) {
    return store.saveDeal(admin, id, title, description, priceCent, stock, active);
  }

  public GroupDeal toggleDeal(User admin, long id) {
    return store.toggleDeal(admin, id);
  }

  public void deleteDeal(User admin, long id) {
    store.deleteDeal(admin, id);
  }

  public Order transition(User admin, long orderId, OrderStatus next) {
    return store.transition(admin, orderId, next);
  }

  public Order verifyCoupon(User admin, String code) {
    return store.verifyCoupon(admin, code);
  }
}
