package com.lumalife.order;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;

@RestController
@RequestMapping("/internal/v1/orders")
public class OrderApi {
  private final OrderStore store;
  public OrderApi(OrderStore store) { this.store = store; }

  @PostMapping
  OrderStore.Order create(@RequestHeader("X-User-Id") long actorUserId,
                          @RequestBody OrderStore.CreateOrderRequest request) {
    if (request.userId() != actorUserId) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能代替其他用户创建订单");
    return store.create(request);
  }

  @GetMapping
  List<OrderStore.Order> orders(@RequestHeader("X-User-Id") long userId) { return store.byUser(userId); }

  @GetMapping("/{id}")
  OrderStore.Order order(@PathVariable long id, @RequestHeader("X-User-Id") long userId) {
    OrderStore.Order order = store.order(id);
    if (order.userId() != userId) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
    return order;
  }

  @PostMapping("/{id}/cancel")
  OrderStore.Order cancel(@PathVariable long id, @RequestHeader("X-User-Id") long userId) { return store.cancel(userId, id); }

  @GetMapping("/cart")
  java.util.Map<Long,Integer> cart(@RequestHeader("X-User-Id") long userId) { return store.cart(userId); }

  @PostMapping("/cart/{productId}")
  java.util.Map<Long,Integer> cartPut(@PathVariable long productId, @RequestHeader("X-User-Id") long userId, @RequestBody CartRequest request) { return store.putCart(userId, productId, request.quantity()); }

  @PostMapping("/cart/{productId}/add")
  java.util.Map<Long,Integer> cartAdd(@PathVariable long productId, @RequestHeader("X-User-Id") long userId, @RequestBody CartRequest request) { return store.addToCart(userId, productId, request.quantity()); }

  @DeleteMapping("/cart/{productId}")
  java.util.Map<Long,Integer> cartDelete(@PathVariable long productId, @RequestHeader("X-User-Id") long userId) { return store.removeCart(userId, productId); }

  @DeleteMapping("/cart")
  void cartClear(@RequestHeader("X-User-Id") long userId) { store.clearCart(userId); }

  @PostMapping("/{id}/pay")
  OrderStore.Order pay(@PathVariable long id, @RequestHeader("X-User-Id") long userId, @RequestBody PayRequest request) {
    store.pay(userId, id, request.amountCent(), request.clientRequestId());
    return store.order(id);
  }

  @PostMapping("/group-buy")
  OrderStore.Order groupBuy(@RequestHeader("X-User-Id") long userId, @RequestBody OrderStore.GroupOrderRequest request) {
    if (request.userId() != userId) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能代替其他用户创建订单");
    return store.createGroupOrder(request);
  }

  @PostMapping("/delivery")
  List<OrderStore.Order> delivery(@RequestHeader("X-User-Id") long userId, @RequestBody OrderStore.DeliveryRequest request) {
    if (request.userId() != userId) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能代替其他用户下单");
    return store.createDeliveryOrders(request);
  }

  @PostMapping("/{id}/receive")
  OrderStore.Order receive(@PathVariable long id, @RequestHeader("X-User-Id") long userId) { return store.receive(userId, id); }

  @PostMapping("/{id}/transition")
  OrderStore.Order transition(@PathVariable long id, @RequestHeader("X-Merchant-Id") long merchantId, @RequestBody TransitionRequest request) { return store.transition(merchantId, id, request.next()); }

  @PostMapping("/coupons/verify")
  OrderStore.Order verifyCoupon(@RequestHeader("X-Merchant-Id") long merchantId, @RequestBody CouponRequest request) {
    try {
      return store.verifyCoupon(merchantId, request.code());
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage());
    }
  }

  @PostMapping("/reviews")
  OrderStore.Review review(@RequestHeader("X-User-Id") long userId, @RequestBody ReviewRequest request) {
    if (request.userId() != userId) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能代替其他用户评价");
    return store.addReview(new OrderStore.ReviewRequest(request.userId(), request.orderId(), request.userName(), request.score(), request.tasteScore(), request.serviceScore(), request.content()));
  }

  @GetMapping("/merchant")
  List<OrderStore.Order> merchantOrders(@RequestHeader("X-Merchant-Id") long merchantId) { return store.merchantOrders(merchantId); }

  @GetMapping("/merchant/reviews")
  List<OrderStore.Review> merchantReviews(@RequestHeader("X-Merchant-Id") long merchantId) { return store.reviews(merchantId); }

  @ExceptionHandler(SecurityException.class)
  org.springframework.http.ResponseEntity<String> forbidden(SecurityException e) { return org.springframework.http.ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }

  @ExceptionHandler(IllegalArgumentException.class)
  org.springframework.http.ResponseEntity<String> badRequest(IllegalArgumentException e) { return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage()); }

  @ExceptionHandler(IllegalStateException.class)
  org.springframework.http.ResponseEntity<String> conflict(IllegalStateException e) { return org.springframework.http.ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage()); }

  @ExceptionHandler(DuplicateKeyException.class)
  org.springframework.http.ResponseEntity<String> duplicateKey(DuplicateKeyException e) { return org.springframework.http.ResponseEntity.status(HttpStatus.CONFLICT).body("幂等键冲突"); }

  record CartRequest(int quantity) {}
  record PayRequest(long amountCent, String clientRequestId) {}
  record TransitionRequest(String next) {}
  record CouponRequest(String code) {}
  record ReviewRequest(long userId, long orderId, String userName, int score, int tasteScore, int serviceScore, String content) {}
}
