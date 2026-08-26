package com.lumalife.controller;

import com.lumalife.common.ApiResponse;
import com.lumalife.common.PageResponse;
import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.domain.Enums.UserRole;
import com.lumalife.domain.Models.CartItem;
import com.lumalife.domain.Models.CartLine;
import com.lumalife.domain.Models.ChatMessage;
import com.lumalife.domain.Models.Address;
import com.lumalife.domain.Models.Category;
import com.lumalife.domain.Models.Merchant;
import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.Product;
import com.lumalife.domain.Models.GroupDeal;
import com.lumalife.domain.Models.Review;
import com.lumalife.domain.Models.User;
import com.lumalife.service.AdminDashboardService;
import com.lumalife.service.AssistantService;
import com.lumalife.service.AuthService;
import com.lumalife.service.CartService;
import com.lumalife.service.CatalogService;
import com.lumalife.service.FavoriteService;
import com.lumalife.service.MerchantAdminService;
import com.lumalife.service.OrderWorkflowService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApiControllers {
  private final AuthService authService;
  private final CatalogService catalogService;
  private final CartService cartService;
  private final OrderWorkflowService orderWorkflowService;
  private final MerchantAdminService merchantAdminService;
  private final AdminDashboardService adminDashboardService;
  private final AssistantService assistantService;
  private final FavoriteService favoriteService;

  public ApiControllers(AuthService authService, CatalogService catalogService, CartService cartService,
                        OrderWorkflowService orderWorkflowService, MerchantAdminService merchantAdminService,
                        AdminDashboardService adminDashboardService, AssistantService assistantService,
                        FavoriteService favoriteService) {
    this.authService = authService;
    this.catalogService = catalogService;
    this.cartService = cartService;
    this.orderWorkflowService = orderWorkflowService;
    this.merchantAdminService = merchantAdminService;
    this.adminDashboardService = adminDashboardService;
    this.favoriteService = favoriteService;
    this.assistantService = assistantService;
  }

  @PostMapping("/auth/login")
  ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
    return ApiResponse.success(authService.login(request.phone(), request.password()));
  }

  @PostMapping("/auth/register")
  ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
    return ApiResponse.success(authService.register(request.phone(), request.password(), request.nickname(), UserRole.USER));
  }

  @PostMapping("/auth/register/merchant")
  ApiResponse<Map<String, Object>> registerMerchant(@RequestBody RegisterRequest request) {
    return ApiResponse.success(authService.register(request.phone(), request.password(), request.nickname(), UserRole.MERCHANT_ADMIN));
  }

  @GetMapping("/auth/me")
  ApiResponse<Map<String, Object>> me(Principal principal) {
    return ApiResponse.success(authService.safeUser(current(principal)));
  }

  @PostMapping("/user/profile")
  ApiResponse<Map<String, Object>> updateProfile(Principal principal, @RequestBody ProfileRequest request) {
    return ApiResponse.success(authService.updateProfile(current(principal), request.nickname(), request.avatarUrl()));
  }

  @GetMapping("/categories")
  ApiResponse<List<Category>> categories() {
    return ApiResponse.success(catalogService.categories());
  }

  @GetMapping("/merchants")
  ApiResponse<PageResponse<Map<String, Object>>> merchants(Principal principal,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) Long categoryId,
                                                            @RequestParam(defaultValue = "recommend") String sort,
                                                            @RequestParam(required = false) Integer minPrice,
                                                            @RequestParam(required = false) Integer maxPrice,
                                                            @RequestParam(required = false) Double minScore,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
    boolean personalized = "recommend".equals(sort) || sort == null;
    Long userId = null;
    if (personalized && principal != null) {
      try {
        User u = current(principal);
        if (u.role() == UserRole.USER) userId = u.id();
      } catch (Exception ignored) {}
    }
    List<Map<String, Object>> all;
    if (userId != null) {
      all = catalogService.merchantsForUser(userId, keyword, categoryId, sort, minPrice, maxPrice, minScore);
    } else {
      all = catalogService.merchants(keyword, categoryId, sort, minPrice, maxPrice, minScore).stream().map(m -> {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", m.id());
        map.put("name", m.name());
        map.put("categoryId", m.categoryId());
        map.put("categoryName", m.categoryName());
        map.put("cover", m.cover());
        map.put("avgScore", m.avgScore());
        map.put("avgPrice", m.avgPrice());
        map.put("monthlySales", m.monthlySales());
        map.put("distanceKm", m.distanceKm());
        map.put("status", m.status());
        map.put("address", m.address());
        map.put("reason", m.reason());
        return map;
      }).toList();
    }
    int from = Math.min((page - 1) * size, all.size());
    int to = Math.min(from + size, all.size());
    return ApiResponse.success(PageResponse.of(all.subList(from, to), page, size, all.size()));
  }

  @GetMapping("/merchants/{id}")
  ApiResponse<Map<String, Object>> merchant(@PathVariable long id) {
    return ApiResponse.success(catalogService.merchantDetail(id));
  }

  @GetMapping("/cart")
  ApiResponse<List<CartItem>> cart(Principal principal) {
    return ApiResponse.success(cartService.cart(current(principal).id()));
  }

  @GetMapping("/cart/detail")
  ApiResponse<List<CartLine>> cartDetail(Principal principal) {
    return ApiResponse.success(cartService.cartDetail(current(principal).id()));
  }

  @GetMapping("/user/addresses")
  ApiResponse<List<Address>> addresses(Principal principal) {
    return ApiResponse.success(authService.addresses(current(principal)));
  }

  @PostMapping("/user/addresses")
  ApiResponse<Address> saveAddress(Principal principal, @RequestBody AddressRequest request) {
    return ApiResponse.success(authService.saveAddress(current(principal), request.id(), request.contactName(), request.phone(), request.detail(), request.defaultAddress()));
  }

  @PostMapping("/user/addresses/{id}/default")
  ApiResponse<Address> setDefaultAddress(Principal principal, @PathVariable long id) {
    return ApiResponse.success(authService.setDefaultAddress(current(principal), id));
  }

  @PostMapping("/user/addresses/{id}/delete")
  ApiResponse<Void> deleteAddress(Principal principal, @PathVariable long id) {
    authService.deleteAddress(current(principal), id);
    return ApiResponse.success(null);
  }

  @GetMapping("/user/favorites")
  ApiResponse<List<Map<String, Object>>> listFavorites(Principal principal) {
    return ApiResponse.success(favoriteService.listFavoriteMerchants(current(principal)));
  }

  @PostMapping("/user/favorites")
  ApiResponse<Void> addFavorite(Principal principal, @RequestBody FavoriteRequest request) {
    favoriteService.addFavorite(current(principal), request.merchantId());
    return ApiResponse.success(null);
  }

  @PostMapping("/user/favorites/{merchantId}/delete")
  ApiResponse<Void> removeFavorite(Principal principal, @PathVariable long merchantId) {
    favoriteService.removeFavorite(current(principal), merchantId);
    return ApiResponse.success(null);
  }

  @PostMapping("/cart/items")
  ApiResponse<List<CartItem>> addCart(Principal principal, @RequestBody CartRequest request) {
    return ApiResponse.success(cartService.addCart(current(principal).id(), request.productId(), request.quantity()));
  }

  @PostMapping("/cart/items/{productId}")
  ApiResponse<List<CartItem>> updateCart(Principal principal, @PathVariable long productId, @RequestBody CartQuantityRequest request) {
    return ApiResponse.success(cartService.updateCartItem(current(principal).id(), productId, request.quantity()));
  }

  @PostMapping("/cart/items/{productId}/delete")
  ApiResponse<List<CartItem>> removeCart(Principal principal, @PathVariable long productId) {
    return ApiResponse.success(cartService.removeCartItem(current(principal).id(), productId));
  }

  @PostMapping("/cart/clear")
  ApiResponse<Void> clearCart(Principal principal) {
    cartService.clearCart(current(principal).id());
    return ApiResponse.success(null);
  }

  @PostMapping("/orders/delivery")
  ApiResponse<List<Order>> deliveryOrder(Principal principal, @RequestBody(required = false) DeliveryOrderRequest request) {
    return ApiResponse.success(orderWorkflowService.createDeliveryOrders(current(principal), request == null ? null : request.addressId()));
  }

  @PostMapping("/orders/group-buy")
  ApiResponse<Order> groupOrder(Principal principal, @RequestBody GroupOrderRequest request) {
    return ApiResponse.success(orderWorkflowService.createGroupOrder(current(principal), request.dealId(), request.quantity()));
  }

  @PostMapping("/payments")
  ApiResponse<Order> pay(Principal principal, @RequestBody PaymentRequest request) {
    return ApiResponse.success(orderWorkflowService.pay(current(principal), request.orderId(), request.clientRequestId()));
  }

  @PostMapping("/orders/{id}/cancel")
  ApiResponse<Order> cancel(Principal principal, @PathVariable long id) {
    return ApiResponse.success(orderWorkflowService.cancel(current(principal), id));
  }

  @PostMapping("/orders/{id}/receive")
  ApiResponse<Order> receive(Principal principal, @PathVariable long id) {
    return ApiResponse.success(orderWorkflowService.receive(current(principal), id));
  }

  @GetMapping("/orders")
  ApiResponse<List<Order>> orders(Principal principal) {
    return ApiResponse.success(orderWorkflowService.userOrders(current(principal)));
  }

  @PostMapping("/reviews")
  ApiResponse<Review> review(Principal principal, @RequestBody ReviewRequest request) {
    return ApiResponse.success(orderWorkflowService.review(current(principal), request.orderId(), request.score(), request.tasteScore(),
      request.serviceScore(), request.content()));
  }

  @GetMapping("/conversations")
  ApiResponse<List<Map<String, Object>>> userConversations(Principal principal) {
    return ApiResponse.success(assistantService.userConversations(current(principal)));
  }

  @GetMapping("/conversations/{merchantId}")
  ApiResponse<List<ChatMessage>> userConversation(Principal principal, @PathVariable long merchantId) {
    return ApiResponse.success(assistantService.userMessages(current(principal), merchantId));
  }

  @PostMapping("/conversations/{merchantId}/messages")
  ApiResponse<List<ChatMessage>> sendUserMessage(Principal principal, @PathVariable long merchantId, @RequestBody MessageRequest request) {
    return ApiResponse.success(assistantService.sendUserMessage(current(principal), merchantId, request.content()));
  }

  @GetMapping("/merchant-admin/orders")
  ApiResponse<List<Order>> merchantOrders(Principal principal) {
    return ApiResponse.success(merchantAdminService.merchantOrders(current(principal)));
  }

  @GetMapping("/merchant-admin/profile")
  ApiResponse<Map<String, Object>> merchantProfile(Principal principal) {
    return ApiResponse.success(merchantAdminService.merchantProfile(current(principal)));
  }

  @PostMapping("/merchant-admin/profile")
  ApiResponse<Map<String, Object>> updateMerchantProfile(Principal principal, @RequestBody MerchantProfileRequest request) {
    return ApiResponse.success(merchantAdminService.updateMerchantNickname(current(principal), request.nickname()));
  }

  @PutMapping("/merchant-admin/profile")
  ApiResponse<Map<String, Object>> replaceMerchantProfile(Principal principal, @RequestBody MerchantProfileRequest request) {
    return ApiResponse.success(merchantAdminService.updateMerchantNickname(current(principal), request.nickname()));
  }

  @GetMapping("/merchant-admin/reviews")
  ApiResponse<List<Review>> merchantReviews(Principal principal) {
    return ApiResponse.success(merchantAdminService.merchantReviews(current(principal)));
  }

  @GetMapping("/merchant-admin/products")
  ApiResponse<List<Product>> merchantProducts(Principal principal) {
    return ApiResponse.success(merchantAdminService.merchantProducts(current(principal)));
  }

  @PostMapping("/merchant-admin/products")
  ApiResponse<Product> saveProduct(Principal principal, @RequestBody ProductRequest request) {
    return ApiResponse.success(merchantAdminService.saveProduct(current(principal), request.id(), request.name(), request.description(), request.priceCent(), request.stock(), request.listed()));
  }

  @PostMapping("/merchant-admin/products/{id}/toggle")
  ApiResponse<Product> toggleProduct(Principal principal, @PathVariable long id) {
    return ApiResponse.success(merchantAdminService.toggleProduct(current(principal), id));
  }

  @PostMapping("/merchant-admin/products/{id}/delete")
  ApiResponse<Void> deleteProduct(Principal principal, @PathVariable long id) {
    merchantAdminService.deleteProduct(current(principal), id);
    return ApiResponse.success(null);
  }

  @GetMapping("/merchant-admin/group-deals")
  ApiResponse<List<GroupDeal>> merchantDeals(Principal principal) {
    return ApiResponse.success(merchantAdminService.merchantDeals(current(principal)));
  }

  @PostMapping("/merchant-admin/group-deals")
  ApiResponse<GroupDeal> saveDeal(Principal principal, @RequestBody DealRequest request) {
    return ApiResponse.success(merchantAdminService.saveDeal(current(principal), request.id(), request.title(), request.description(), request.priceCent(), request.stock(), request.active()));
  }

  @PostMapping("/merchant-admin/group-deals/{id}/toggle")
  ApiResponse<GroupDeal> toggleDeal(Principal principal, @PathVariable long id) {
    return ApiResponse.success(merchantAdminService.toggleDeal(current(principal), id));
  }

  @PostMapping("/merchant-admin/group-deals/{id}/delete")
  ApiResponse<Void> deleteDeal(Principal principal, @PathVariable long id) {
    merchantAdminService.deleteDeal(current(principal), id);
    return ApiResponse.success(null);
  }

  @PostMapping("/merchant-admin/orders/{id}/transition")
  ApiResponse<Order> transition(Principal principal, @PathVariable long id, @RequestBody TransitionRequest request) {
    return ApiResponse.success(merchantAdminService.transition(current(principal), id, request.next()));
  }

  @PostMapping("/merchant-admin/coupons/verify")
  ApiResponse<Order> verify(Principal principal, @RequestBody VerifyRequest request) {
    return ApiResponse.success(merchantAdminService.verifyCoupon(current(principal), request.code()));
  }

  @GetMapping("/merchant-admin/conversations")
  ApiResponse<List<Map<String, Object>>> merchantConversations(Principal principal) {
    return ApiResponse.success(assistantService.merchantConversations(current(principal)));
  }

  @GetMapping("/merchant-admin/conversations/{userId}")
  ApiResponse<List<ChatMessage>> merchantConversation(Principal principal, @PathVariable long userId) {
    return ApiResponse.success(assistantService.merchantMessages(current(principal), userId));
  }

  @PostMapping("/merchant-admin/conversations/{userId}/messages")
  ApiResponse<List<ChatMessage>> sendMerchantMessage(Principal principal, @PathVariable long userId, @RequestBody MessageRequest request) {
    return ApiResponse.success(assistantService.sendMerchantMessage(current(principal), userId, request.content()));
  }

  @PostMapping("/merchant-admin/assistant/ask")
  ApiResponse<Map<String, String>> merchantAssistant(Principal principal, @RequestBody AskRequest request) {
    return ApiResponse.success(Map.of("answer", assistantService.askForMerchant(current(principal), request.question())));
  }

  @GetMapping("/admin/metrics")
  ApiResponse<Map<String, Object>> metrics() {
    return ApiResponse.success(adminDashboardService.metrics());
  }

  @PostMapping("/assistant/ask")
  ApiResponse<Map<String, String>> assistant(@RequestBody AskRequest request) {
    return ApiResponse.success(Map.of("answer", assistantService.ask(request.question())));
  }

  private User current(Principal principal) {
    return authService.current(principal.getName());
  }

  public record LoginRequest(String phone, String password) {}
  public record RegisterRequest(String phone, String password, String nickname, UserRole role) {}
  public record ProfileRequest(String nickname, String avatarUrl) {}
  public record AddressRequest(Long id, String contactName, String phone, String detail, boolean defaultAddress) {}
  public record CartRequest(long productId, int quantity) {}
  public record CartQuantityRequest(int quantity) {}
  public record ProductRequest(Long id, String name, String description, long priceCent, int stock, boolean listed) {}
  public record MerchantProfileRequest(String nickname) {}
  public record DealRequest(Long id, String title, String description, long priceCent, int stock, boolean active) {}
  public record DeliveryOrderRequest(Long addressId) {}
  public record GroupOrderRequest(long dealId, int quantity) {}
  public record PaymentRequest(long orderId, String clientRequestId) {}
  public record TransitionRequest(OrderStatus next) {}
  public record VerifyRequest(String code) {}
  public record ReviewRequest(long orderId, int score, int tasteScore, int serviceScore, String content) {}
  public record AskRequest(String question) {}
  public record MessageRequest(String content) {}
  public record FavoriteRequest(long merchantId) {}
}
