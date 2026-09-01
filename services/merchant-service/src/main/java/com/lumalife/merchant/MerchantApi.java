package com.lumalife.merchant;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/internal/v1")
public class MerchantApi {
  private final MerchantStore store;
  public MerchantApi(MerchantStore store) { this.store = store; }

  @GetMapping("/categories")
  List<java.util.Map<String, Object>> categories() { return store.categories(); }

  @GetMapping("/merchants")
  List<MerchantStore.Merchant> merchants(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(required = false) Integer minPrice,
                                         @RequestParam(required = false) Integer maxPrice,
                                         @RequestParam(required = false) Double minScore) {
    return store.search(keyword, categoryId, sort, minPrice, maxPrice, minScore);
  }

  @GetMapping("/merchants/{id}")
  MerchantStore.Merchant merchant(@PathVariable long id) { return readResource(() -> store.merchant(id)); }

  @PostMapping("/merchants/provision")
  MerchantStore.Merchant provision(@RequestBody ProvisionRequest request) { return store.provision(request.name()); }

  @GetMapping("/merchants/{id}/profile")
  java.util.Map<String, Object> profile(@PathVariable long id) { return store.profile(id); }

  @PutMapping("/merchants/{id}/profile")
  java.util.Map<String, Object> updateProfile(@PathVariable long id, @RequestHeader("X-Merchant-Id") long actor,
                                               @RequestBody ProfileRequest request) {
    if (id != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能修改其他商家的资料");
    return store.profile(store.updateName(id, request.name()).id());
  }

  @GetMapping("/users/{userId}/favorites")
  List<Long> favorites(@PathVariable long userId, @RequestHeader("X-User-Id") long actor) {
    if (userId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能查看其他用户的收藏");
    return store.favorites(userId);
  }

  @GetMapping("/users/{userId}/favorite-merchants")
  List<java.util.Map<String, Object>> favoriteMerchants(@PathVariable long userId, @RequestHeader("X-User-Id") long actor) {
    if (userId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能查看其他用户的收藏");
    return store.favoriteMerchants(userId);
  }

  @PostMapping("/users/{userId}/favorites/{merchantId}")
  void addFavorite(@PathVariable long userId, @PathVariable long merchantId, @RequestHeader("X-User-Id") long actor) {
    if (userId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能操作其他用户的收藏");
    store.addFavorite(userId, merchantId);
  }

  @DeleteMapping("/users/{userId}/favorites/{merchantId}")
  void removeFavorite(@PathVariable long userId, @PathVariable long merchantId, @RequestHeader("X-User-Id") long actor) {
    if (userId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能操作其他用户的收藏");
    store.removeFavorite(userId, merchantId);
  }

  @GetMapping("/users/{userId}/conversations")
  List<java.util.Map<String, Object>> userConversations(@PathVariable long userId, @RequestHeader("X-User-Id") long actor) {
    if (userId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能查看其他用户的会话");
    return store.conversationSummaries(userId, null, true);
  }

  @GetMapping("/users/{userId}/conversations/{merchantId}")
  List<MerchantStore.ChatMessage> userConversation(@PathVariable long userId, @PathVariable long merchantId, @RequestHeader("X-User-Id") long actor) {
    if (userId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能查看其他用户的会话");
    return store.conversation(userId, merchantId);
  }

  @PostMapping("/users/{userId}/conversations/{merchantId}/messages")
  List<MerchantStore.ChatMessage> userMessage(@PathVariable long userId, @PathVariable long merchantId, @RequestHeader("X-User-Id") long actor, @RequestBody MessageRequest request) {
    if (userId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能操作其他用户的会话");
    return store.sendUserMessage(userId, merchantId, request.content(), request.assistantAnswer() == null ? null : ignored -> request.assistantAnswer());
  }

  @GetMapping("/merchants/{merchantId}/conversations")
  List<java.util.Map<String, Object>> merchantConversations(@PathVariable long merchantId, @RequestHeader("X-Merchant-Id") long actor) {
    if (merchantId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能查看其他商家的会话");
    return store.conversationSummaries(0, merchantId, false);
  }

  @GetMapping("/merchants/{merchantId}/conversations/{userId}")
  List<MerchantStore.ChatMessage> merchantConversation(@PathVariable long merchantId, @PathVariable long userId, @RequestHeader("X-Merchant-Id") long actor) {
    if (merchantId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能查看其他商家的会话");
    return store.conversation(userId, merchantId);
  }

  @PostMapping("/merchants/{merchantId}/conversations/{userId}/messages")
  List<MerchantStore.ChatMessage> merchantMessage(@PathVariable long merchantId, @PathVariable long userId, @RequestHeader("X-Merchant-Id") long actor, @RequestBody MessageRequest request) {
    if (merchantId != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能操作其他商家的会话");
    return store.sendMerchantMessage(merchantId, userId, request.content(), "商家客服");
  }

  @GetMapping("/merchants/{id}/products")
  List<MerchantStore.Product> products(@PathVariable long id,
                                       @RequestParam(defaultValue = "false") boolean listedOnly) {
    return readResource(() -> store.products(id, listedOnly));
  }

  @GetMapping("/deals/{id}")
  MerchantStore.GroupDeal deal(@PathVariable long id) { return readResource(() -> store.deal(id)); }

  @GetMapping("/products/{id}")
  MerchantStore.Product product(@PathVariable long id) { return readResource(() -> store.product(id)); }

  @PostMapping("/inventory/reservations")
  ResponseEntity<MerchantStore.InventoryReservation> reserveInventory(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestBody MerchantStore.ReservationRequest request) {
    try {
      return ResponseEntity.ok(store.reserveInventory(request, idempotencyKey));
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
    } catch (IllegalStateException error) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage());
    }
  }

  @GetMapping("/inventory/reservations/{orderId}")
  MerchantStore.InventoryReservation inventoryReservation(@PathVariable long orderId) {
    return readResource(() -> store.inventoryReservation(orderId));
  }

  @PostMapping("/inventory/reservations/{orderId}:release")
  ResponseEntity<MerchantStore.InventoryReservation> releaseInventory(
      @PathVariable long orderId, @RequestHeader("Idempotency-Key") String idempotencyKey) {
    try {
      return ResponseEntity.ok(store.releaseInventory(orderId, idempotencyKey));
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
    } catch (IllegalStateException error) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage());
    }
  }

  @PostMapping("/inventory/reservations/{orderId}:confirm")
  ResponseEntity<MerchantStore.InventoryReservation> confirmInventory(@PathVariable long orderId) {
    try {
      return ResponseEntity.ok(store.confirmInventory(orderId));
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
    } catch (IllegalStateException error) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage());
    }
  }

  @PostMapping("/merchants/{id}/products")
  MerchantStore.Product saveProduct(@PathVariable long id, @RequestHeader("X-Merchant-Id") long actorMerchantId,
                                    @RequestBody MerchantStore.ProductRequest request) {
    if (id != actorMerchantId) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能写入其他商家的商品");
    try {
      return store.saveProduct(id, request);
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
    }
  }

  @PostMapping("/merchants/{id}/products/{productId}/toggle")
  MerchantStore.Product toggleProduct(@PathVariable long id, @PathVariable long productId, @RequestHeader("X-Merchant-Id") long actor) {
    if (id != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能维护其他商家的商品"); return store.toggleProduct(id, productId);
  }

  @DeleteMapping("/merchants/{id}/products/{productId}")
  void deleteProduct(@PathVariable long id, @PathVariable long productId, @RequestHeader("X-Merchant-Id") long actor) {
    if (id != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能维护其他商家的商品"); store.deleteProduct(id, productId);
  }

  @GetMapping("/merchants/{id}/deals")
  List<MerchantStore.GroupDeal> deals(@PathVariable long id,
                                      @RequestParam(defaultValue = "false") boolean activeOnly) {
    return store.deals(id, activeOnly);
  }

  @PostMapping("/merchants/{id}/deals")
  MerchantStore.GroupDeal saveDeal(@PathVariable long id, @RequestHeader("X-Merchant-Id") long actor, @RequestBody MerchantStore.DealRequest request) {
    if (id != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能维护其他商家的套餐");
    try {
      return store.saveDeal(id, request);
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
    }
  }

  @PostMapping("/merchants/{id}/deals/{dealId}/toggle")
  MerchantStore.GroupDeal toggleDeal(@PathVariable long id, @PathVariable long dealId, @RequestHeader("X-Merchant-Id") long actor) {
    if (id != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能维护其他商家的套餐"); return store.toggleDeal(id, dealId);
  }

  @DeleteMapping("/merchants/{id}/deals/{dealId}")
  void deleteDeal(@PathVariable long id, @PathVariable long dealId, @RequestHeader("X-Merchant-Id") long actor) {
    if (id != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能维护其他商家的套餐"); store.deleteDeal(id, dealId);
  }

  record ProfileRequest(String name) {}
  record ProvisionRequest(String name) {}
  record MessageRequest(String content, String assistantAnswer) {}

  private <T> T readResource(java.util.function.Supplier<T> operation) {
    try {
      return operation.get();
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage());
    }
  }
}
