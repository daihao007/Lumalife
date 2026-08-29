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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

  @PostMapping("/{id}/cancel")
  OrderStore.Order cancel(@PathVariable long id, @RequestHeader("X-User-Id") long userId) { return store.cancel(userId, id); }

  @GetMapping("/cart")
  java.util.Map<Long,Integer> cart(@RequestHeader("X-User-Id") long userId) { return store.cart(userId); }

  @PostMapping("/cart/{productId}")
  java.util.Map<Long,Integer> cartPut(@PathVariable long productId, @RequestHeader("X-User-Id") long userId, @RequestBody CartRequest request) { return store.putCart(userId, productId, request.quantity()); }

  @PostMapping("/{id}/pay")
  OrderStore.Payment pay(@PathVariable long id, @RequestHeader("X-User-Id") long userId, @RequestBody PayRequest request) { return store.pay(userId, id, request.amountCent(), request.clientRequestId()); }

  record CartRequest(int quantity) {}
  record PayRequest(long amountCent, String clientRequestId) {}
}
