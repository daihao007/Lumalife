package com.lumalife.service;

import com.lumalife.domain.Models.Category;
import com.lumalife.domain.Models.Merchant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
  private final DemoStore store;

  public CatalogService(DemoStore store) {
    this.store = store;
  }

  public List<Category> categories() {
    return store.categories();
  }

  public List<Merchant> merchants(String keyword, Long categoryId, String sort, Integer minPrice, Integer maxPrice, Double minScore) {
    return store.merchants(keyword, categoryId, sort, minPrice, maxPrice, minScore);
  }

  /**
   * 个性化推荐：返回带动态 reason 的商户列表
   */
  public List<Map<String, Object>> merchantsForUser(long userId, String keyword, Long categoryId,
                                                      String sort, Integer minPrice, Integer maxPrice, Double minScore) {
    return store.merchantsForUser(userId, keyword, categoryId, sort, minPrice, maxPrice, minScore);
  }

  public Map<String, Object> merchantDetail(long id) {
    return store.merchantDetail(id);
  }
}
