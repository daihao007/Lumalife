package com.lumalife.service.boundary;

import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.domain.Models.ChatMessage;
import com.lumalife.domain.Models.GroupDeal;
import com.lumalife.domain.Models.Merchant;
import com.lumalife.domain.Models.Product;
import com.lumalife.domain.Models.User;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Merchant/catalog capability boundary. IDs crossing this port are references, not shared entities. */
public interface MerchantServicePort {
  List<com.lumalife.domain.Models.Category> categories();

  List<Merchant> merchants(String keyword, Long categoryId, String sort, Integer minPrice, Integer maxPrice, Double minScore);

  List<Map<String, Object>> merchantsForUser(long userId, String keyword, Long categoryId,
                                               String sort, Integer minPrice, Integer maxPrice, Double minScore);

  Map<String, Object> merchantDetail(long id);

  Map<String, Object> merchantProfile(User admin);

  Map<String, Object> updateMerchantNickname(User admin, String nickname);

  List<Product> merchantProducts(User admin);

  Product saveProduct(User admin, Long id, String name, String description, long priceCent, int stock, boolean listed);

  Product toggleProduct(User admin, long id);

  void deleteProduct(User admin, long id);

  List<GroupDeal> merchantDeals(User admin);

  GroupDeal saveDeal(User admin, Long id, String title, String description, long priceCent, int stock, boolean active);

  GroupDeal toggleDeal(User admin, long id);

  void deleteDeal(User admin, long id);

  void addFavorite(long userId, long merchantId);

  void removeFavorite(long userId, long merchantId);

  List<Long> listFavorites(long userId);

  List<Map<String, Object>> listFavoriteMerchants(long userId);

  List<Map<String, Object>> userConversationSummaries(User user);

  List<Map<String, Object>> merchantConversationSummaries(User admin);

  List<ChatMessage> userConversation(User user, long merchantId);

  List<ChatMessage> merchantConversation(User admin, long userId);

  List<ChatMessage> sendUserMessage(User user, long merchantId, String content,
                                    Function<List<ChatMessage>, String> aiResponder);

  List<ChatMessage> sendMerchantMessage(User admin, long userId, String content);

  String assistantFallback(String question);
}
