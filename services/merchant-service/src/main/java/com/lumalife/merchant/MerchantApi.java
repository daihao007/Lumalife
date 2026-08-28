package com.lumalife.merchant;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1")
public class MerchantApi {
  private final MerchantStore store;
  public MerchantApi(MerchantStore store) { this.store = store; }

  @GetMapping("/merchants")
  List<MerchantStore.Merchant> merchants(@RequestParam(required = false) String keyword) { return store.search(keyword); }

  @GetMapping("/merchants/{id}")
  MerchantStore.Merchant merchant(@PathVariable long id) { return store.merchant(id); }

  @GetMapping("/merchants/{id}/products")
  List<MerchantStore.Product> products(@PathVariable long id) { return store.products(id); }

  @PostMapping("/merchants/{id}/products")
  MerchantStore.Product saveProduct(@PathVariable long id, @RequestHeader("X-Merchant-Id") long actorMerchantId,
                                    @RequestBody MerchantStore.ProductRequest request) {
    if (id != actorMerchantId) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能写入其他商家的商品");
    return store.saveProduct(id, request);
  }
}
