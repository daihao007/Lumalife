package com.lumalife.service;

import com.lumalife.domain.Models.GroupDeal;
import com.lumalife.domain.Models.Product;
import com.lumalife.domain.Models.User;
import java.util.List;
import java.util.Map;
import com.lumalife.service.boundary.MerchantServicePort;
import com.lumalife.service.boundary.IdentityServicePort;
import org.springframework.stereotype.Service;

@Service
public class MerchantAdminService {
  private final MerchantServicePort merchant;
  private final IdentityServicePort identity;

  public MerchantAdminService(MerchantServicePort merchant, IdentityServicePort identity) {
    this.merchant = merchant;
    this.identity = identity;
  }

  public Map<String, Object> merchantProfile(User admin) {
    return merchant.merchantProfile(admin);
  }

  public Map<String, Object> updateMerchantNickname(User admin, String nickname) {
    Map<String, Object> updated = merchant.updateMerchantNickname(admin, nickname);
    try {
      identity.updateProfile(admin, nickname, admin.avatarUrl());
      return updated;
    } catch (RuntimeException error) {
      try {
        merchant.updateMerchantNickname(admin, admin.nickname());
      } catch (RuntimeException rollbackError) {
        error.addSuppressed(rollbackError);
      }
      throw error;
    }
  }

  public List<Product> merchantProducts(User admin) {
    return merchant.merchantProducts(admin);
  }

  public Product saveProduct(User admin, Long id, String name, String description, long priceCent, int stock, boolean listed) {
    return merchant.saveProduct(admin, id, name, description, priceCent, stock, listed);
  }

  public Product toggleProduct(User admin, long id) {
    return merchant.toggleProduct(admin, id);
  }

  public void deleteProduct(User admin, long id) {
    merchant.deleteProduct(admin, id);
  }

  public List<GroupDeal> merchantDeals(User admin) {
    return merchant.merchantDeals(admin);
  }

  public GroupDeal saveDeal(User admin, Long id, String title, String description, long priceCent, int stock, boolean active) {
    return merchant.saveDeal(admin, id, title, description, priceCent, stock, active);
  }

  public GroupDeal toggleDeal(User admin, long id) {
    return merchant.toggleDeal(admin, id);
  }

  public void deleteDeal(User admin, long id) {
    merchant.deleteDeal(admin, id);
  }
}
