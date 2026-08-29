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

@RestController
@RequestMapping("/internal/v1/orders")
public class OrderApi {
  private final OrderStore store;
  public OrderApi(OrderStore store) { this.store = store; }

  @PostMapping
  OrderStore.Order create(@RequestBody OrderStore.CreateOrderRequest request) { return store.create(request); }

  @GetMapping
  List<OrderStore.Order> orders(@RequestHeader("X-User-Id") long userId) { return store.byUser(userId); }

  @PostMapping("/{id}/cancel")
  OrderStore.Order cancel(@PathVariable long id, @RequestHeader("X-User-Id") long userId) { return store.cancel(userId, id); }
}
