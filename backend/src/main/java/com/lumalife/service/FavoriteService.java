package com.lumalife.service;

import com.lumalife.domain.Models.User;
import java.util.List;
import java.util.Map;
import com.lumalife.service.boundary.MerchantServicePort;
import org.springframework.stereotype.Service;

@Service
public class FavoriteService {
  private final MerchantServicePort merchant;

  public FavoriteService(MerchantServicePort merchant) {
    this.merchant = merchant;
  }

  public void addFavorite(User user, long merchantId) {
    merchant.addFavorite(user.id(), merchantId);
  }

  public void removeFavorite(User user, long merchantId) {
    merchant.removeFavorite(user.id(), merchantId);
  }

  public List<Long> listFavorites(User user) {
    return merchant.listFavorites(user.id());
  }

  public List<Map<String, Object>> listFavoriteMerchants(User user) {
    return merchant.listFavoriteMerchants(user.id());
  }
}
