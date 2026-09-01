package com.lumalife.order;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only projection exposed to merchant-service for public shop details. */
@RestController
@RequestMapping("/internal/v1/merchants")
public class MerchantReviewProjectionApi {
  private final OrderStore store;

  public MerchantReviewProjectionApi(OrderStore store) { this.store = store; }

  @GetMapping("/{merchantId}/reviews")
  List<OrderStore.Review> reviews(@PathVariable long merchantId) { return store.reviews(merchantId); }
}
