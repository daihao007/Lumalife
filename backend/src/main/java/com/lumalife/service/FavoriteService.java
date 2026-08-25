package com.lumalife.service;

import com.lumalife.domain.Models.User;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FavoriteService {
  private final DemoStore store;

  public FavoriteService(DemoStore store) {
    this.store = store;
  }

  public void addFavorite(User user, long merchantId) {
    store.addFavorite(user.id(), merchantId);
  }

  public void removeFavorite(User user, long merchantId) {
    store.removeFavorite(user.id(), merchantId);
  }

  public List<Long> listFavorites(User user) {
    return store.listFavorites(user.id());
  }

  public List<Map<String, Object>> listFavoriteMerchants(User user) {
    return store.listFavoriteMerchants(user.id());
  }
}
