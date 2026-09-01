package com.lumalife.merchant;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
  List<MerchantStore.Merchant> merchants(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(defaultValue = "recommend") String sort,
                                         @RequestParam(required = false) Integer minPrice,
                                         @RequestParam(required = false) Integer maxPrice,
                                         @RequestParam(required = false) Double minScore) {
    return store.search(keyword, categoryId, sort, minPrice, maxPrice, minScore);
  }

  @GetMapping("/merchants/{id}")
  MerchantStore.Merchant merchant(@PathVariable long id) { return readResource(() -> store.merchant(id)); }

  @GetMapping("/merchants/{id}/products")
  List<MerchantStore.Product> products(@PathVariable long id) { return readResource(() -> store.products(id)); }

  @GetMapping("/deals/{id}")
  MerchantStore.GroupDeal deal(@PathVariable long id) { return readResource(() -> store.deal(id)); }

  @GetMapping("/products/{id}")
  MerchantStore.Product product(@PathVariable long id) { return readResource(() -> store.product(id)); }

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
  List<MerchantStore.GroupDeal> deals(@PathVariable long id) { return store.deals(id); }

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

  private <T> T readResource(java.util.function.Supplier<T> operation) {
    try {
      return operation.get();
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage());
    }
  }
}
