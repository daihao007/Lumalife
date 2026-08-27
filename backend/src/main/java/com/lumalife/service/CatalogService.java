package com.lumalife.service;

import com.lumalife.domain.Models.Category;
import com.lumalife.domain.Models.Merchant;
import java.util.List;
import java.util.Map;
import com.lumalife.service.boundary.MerchantServicePort;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
  private final MerchantServicePort merchant;

  public CatalogService(MerchantServicePort merchant) {
    this.merchant = merchant;
  }

  public List<Category> categories() {
    return merchant.categories();
  }

  public List<Merchant> merchants(String keyword, Long categoryId, String sort, Integer minPrice, Integer maxPrice, Double minScore) {
    return merchant.merchants(keyword, categoryId, sort, minPrice, maxPrice, minScore);
  }

  /**
   * 个性化推荐：返回带动态 reason 的商户列表
   */
  public List<Map<String, Object>> merchantsForUser(long userId, String keyword, Long categoryId,
                                                      String sort, Integer minPrice, Integer maxPrice, Double minScore) {
    return merchant.merchantsForUser(userId, keyword, categoryId, sort, minPrice, maxPrice, minScore);
  }

  public Map<String, Object> merchantDetail(long id) {
    return merchant.merchantDetail(id);
  }
}
