package com.lumalife.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.util.Arrays;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = "lumalife.internal.service-token=test-internal-token")
class MerchantServiceBusinessTest {
  @Autowired private TestRestTemplate http;

  @Test
  void exposesMerchantOwnedCatalog() {
    HttpHeaders headers = serviceHeaders();
    MerchantStore.Merchant merchant = http.exchange("/internal/v1/merchants/2", HttpMethod.GET,
      new HttpEntity<>(headers), MerchantStore.Merchant.class).getBody();
    assertThat(merchant.name()).isEqualTo("晨雾咖啡局");
    MerchantStore.Product[] products = http.exchange("/internal/v1/merchants/1/products", HttpMethod.GET,
      new HttpEntity<>(headers), MerchantStore.Product[].class).getBody();
    assertThat(products).isNotEmpty();
  }

  @Test
  void exposesCompleteMerchantCardsIncludingCovers() {
    MerchantStore.Merchant[] merchants = http.exchange("/internal/v1/merchants", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), MerchantStore.Merchant[].class).getBody();

    assertThat(merchants).hasSizeGreaterThanOrEqualTo(4);
    assertThat(merchants).allSatisfy(merchant -> assertThat(merchant.cover()).isNotBlank());
  }

  @Test
  void ctRtMer04To05And09ReadsProductDealAndMerchantDeals() {
    HttpHeaders headers = serviceHeaders();
    MerchantStore.Product product = http.exchange("/internal/v1/products/1001", HttpMethod.GET,
      new HttpEntity<>(headers), MerchantStore.Product.class).getBody();
    MerchantStore.GroupDeal deal = http.exchange("/internal/v1/deals/1", HttpMethod.GET,
      new HttpEntity<>(headers), MerchantStore.GroupDeal.class).getBody();
    MerchantStore.GroupDeal[] merchantDeals = http.exchange("/internal/v1/merchants/1/deals", HttpMethod.GET,
      new HttpEntity<>(headers), MerchantStore.GroupDeal[].class).getBody();

    assertThat(product).extracting(MerchantStore.Product::merchantId, MerchantStore.Product::listed).containsExactly(1L, true);
    assertThat(deal).extracting(MerchantStore.GroupDeal::merchantId, MerchantStore.GroupDeal::active).containsExactly(1L, true);
    assertThat(merchantDeals).anySatisfy(item -> assertThat(item.id()).isEqualTo(deal.id()));
  }

  @Test
  void requiresServiceAndMerchantIdentityForCatalogWrites() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-Merchant-Id", "2");
    ResponseEntity<MerchantStore.Product> response = http.exchange("/internal/v1/merchants/1/products", HttpMethod.POST,
      new HttpEntity<>(new MerchantStore.ProductRequest(null, "越权商品", "", 100, 1, true), headers), MerchantStore.Product.class);
    assertThat(response.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void ctRtMer07To12PublishesTogglesAndDeletesOnlyTheMerchantsCatalog() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-Merchant-Id", "1");
    String runId = UUID.randomUUID().toString();
    MerchantStore.Product product = http.exchange("/internal/v1/merchants/1/products", HttpMethod.POST,
      new HttpEntity<>(new MerchantStore.ProductRequest(null, "CT 商品 " + runId, "临时契约数据", 1234, 2, true), headers), MerchantStore.Product.class).getBody();
    boolean productDeleted = false;
    boolean dealDeleted = false;
    MerchantStore.GroupDeal deal = null;
    try {
      deal = http.exchange("/internal/v1/merchants/1/deals", HttpMethod.POST,
        new HttpEntity<>(new MerchantStore.DealRequest(null, "CT 套餐 " + runId, "临时契约数据", 2345, 2, true), headers), MerchantStore.GroupDeal.class).getBody();
      long dealId = deal.id();
      MerchantStore.Product unlisted = http.exchange("/internal/v1/merchants/1/products/" + product.id() + "/toggle", HttpMethod.POST,
        new HttpEntity<>(headers), MerchantStore.Product.class).getBody();
      MerchantStore.GroupDeal inactive = http.exchange("/internal/v1/merchants/1/deals/" + dealId + "/toggle", HttpMethod.POST,
        new HttpEntity<>(headers), MerchantStore.GroupDeal.class).getBody();
      assertThat(unlisted.listed()).isFalse();
      assertThat(inactive.active()).isFalse();
      assertThat(http.exchange("/internal/v1/merchants/1/products", HttpMethod.GET, new HttpEntity<>(serviceHeaders()), MerchantStore.Product[].class).getBody())
        .anySatisfy(item -> assertThat(item.id()).isEqualTo(product.id()));
      assertThat(http.exchange("/internal/v1/merchants/1/deals", HttpMethod.GET, new HttpEntity<>(serviceHeaders()), MerchantStore.GroupDeal[].class).getBody())
        .anySatisfy(item -> assertThat(item.id()).isEqualTo(dealId));

      http.exchange("/internal/v1/merchants/1/products/" + product.id(), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
      productDeleted = true;
      http.exchange("/internal/v1/merchants/1/deals/" + dealId, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
      dealDeleted = true;
      MerchantStore.Product[] products = http.exchange("/internal/v1/merchants/1/products", HttpMethod.GET,
        new HttpEntity<>(serviceHeaders()), MerchantStore.Product[].class).getBody();
      MerchantStore.GroupDeal[] deals = http.exchange("/internal/v1/merchants/1/deals", HttpMethod.GET,
        new HttpEntity<>(serviceHeaders()), MerchantStore.GroupDeal[].class).getBody();
      assertThat(Arrays.stream(products).map(MerchantStore.Product::id).toList()).doesNotContain(product.id());
      assertThat(Arrays.stream(deals).map(MerchantStore.GroupDeal::id).toList()).doesNotContain(dealId);
    } finally {
      if (!productDeleted) http.exchange("/internal/v1/merchants/1/products/" + product.id(), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
      if (deal != null && !dealDeleted) http.exchange("/internal/v1/merchants/1/deals/" + deal.id(), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }
  }

  @Test
  void supportsMerchantProvisioningAndProductNameSearch() {
    MerchantStore.Merchant provisioned = http.postForObject("/internal/v1/merchants/provision",
      new HttpEntity<>(java.util.Map.of("name", "新店铺"), serviceHeaders()), MerchantStore.Merchant.class);
    assertThat(provisioned.id()).isGreaterThan(3000);
    MerchantStore.Merchant[] matches = http.exchange("/internal/v1/merchants?keyword=毛血旺", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), MerchantStore.Merchant[].class).getBody();
    assertThat(matches).extracting(MerchantStore.Merchant::id).contains(1L);
  }

  @Test
  void reservesConfirmsAndReleasesMerchantOwnedInventoryIdempotently() {
    long orderId = Math.abs(System.nanoTime());
    String reserveKey = "inventory-" + UUID.randomUUID();
    String releaseKey = "release-" + UUID.randomUUID();
    HttpHeaders reserveHeaders = serviceHeaders();
    reserveHeaders.set("Idempotency-Key", reserveKey);
    MerchantStore.Product before = http.exchange("/internal/v1/products/1001", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), MerchantStore.Product.class).getBody();
    MerchantStore.ReservationRequest request = new MerchantStore.ReservationRequest(
      orderId, Instant.now().plusSeconds(300),
      java.util.List.of(new MerchantStore.ReservationItem("PRODUCT", 1001, 2, 0)));

    ResponseEntity<MerchantStore.InventoryReservation> reserved = http.exchange("/internal/v1/inventory/reservations",
      HttpMethod.POST, new HttpEntity<>(request, reserveHeaders), MerchantStore.InventoryReservation.class);
    assertThat(reserved.getStatusCode().value()).isEqualTo(200);
    assertThat(reserved.getBody().status()).isEqualTo("RESERVED");

    ResponseEntity<MerchantStore.InventoryReservation> replay = http.exchange("/internal/v1/inventory/reservations",
      HttpMethod.POST, new HttpEntity<>(request, reserveHeaders), MerchantStore.InventoryReservation.class);
    assertThat(replay.getBody().status()).isEqualTo("RESERVED");
    MerchantStore.Product held = http.exchange("/internal/v1/products/1001", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), MerchantStore.Product.class).getBody();
    assertThat(held.stock()).isEqualTo(before.stock() - 2);

    HttpHeaders releaseHeaders = serviceHeaders();
    releaseHeaders.set("Idempotency-Key", releaseKey);
    MerchantStore.InventoryReservation released = http.exchange("/internal/v1/inventory/reservations/" + orderId + ":release",
      HttpMethod.POST, new HttpEntity<>(releaseHeaders), MerchantStore.InventoryReservation.class).getBody();
    assertThat(released.status()).isEqualTo("RELEASED");
    MerchantStore.Product restored = http.exchange("/internal/v1/products/1001", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), MerchantStore.Product.class).getBody();
    assertThat(restored.stock()).isEqualTo(before.stock());

    long confirmedOrderId = orderId + 1;
    String confirmedKey = "inventory-" + UUID.randomUUID();
    HttpHeaders confirmedHeaders = serviceHeaders();
    confirmedHeaders.set("Idempotency-Key", confirmedKey);
    MerchantStore.ReservationRequest confirmedRequest = new MerchantStore.ReservationRequest(
      confirmedOrderId, Instant.now().plusSeconds(300),
      java.util.List.of(new MerchantStore.ReservationItem("PRODUCT", 1001, 1, 0)));
    http.exchange("/internal/v1/inventory/reservations", HttpMethod.POST,
      new HttpEntity<>(confirmedRequest, confirmedHeaders), MerchantStore.InventoryReservation.class);
    MerchantStore.InventoryReservation confirmed = http.exchange("/internal/v1/inventory/reservations/" + confirmedOrderId + ":confirm",
      HttpMethod.POST, new HttpEntity<>(serviceHeaders()), MerchantStore.InventoryReservation.class).getBody();
    assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    HttpHeaders rejectedReleaseHeaders = serviceHeaders();
    rejectedReleaseHeaders.set("Idempotency-Key", "release-" + UUID.randomUUID());
    ResponseEntity<String> rejectedRelease = http.exchange("/internal/v1/inventory/reservations/" + confirmedOrderId + ":release",
      HttpMethod.POST, new HttpEntity<>(rejectedReleaseHeaders), String.class);
    assertThat(rejectedRelease.getStatusCode().value()).isEqualTo(409);
  }

  @Test
  void requiresServiceTokenForInternalCatalogReads() {
    ResponseEntity<String> response = http.getForEntity("/internal/v1/products/1001", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void ctRtMer01To05RejectsUnknownResourcesAndKeepsSearchDeterministic() {
    MerchantStore.Merchant[] noMatches = http.exchange("/internal/v1/merchants?keyword=不存在的契约关键词",
      HttpMethod.GET, new HttpEntity<>(serviceHeaders()), MerchantStore.Merchant[].class).getBody();
    assertThat(noMatches).isEmpty();

    ResponseEntity<String> unknownMerchant = http.exchange("/internal/v1/merchants/999999999",
      HttpMethod.GET, new HttpEntity<>(serviceHeaders()), String.class);
    ResponseEntity<String> unknownProduct = http.exchange("/internal/v1/products/999999999",
      HttpMethod.GET, new HttpEntity<>(serviceHeaders()), String.class);
    ResponseEntity<String> unknownDeal = http.exchange("/internal/v1/deals/999999999",
      HttpMethod.GET, new HttpEntity<>(serviceHeaders()), String.class);

    assertThat(unknownMerchant.getStatusCode().value()).isEqualTo(404);
    assertThat(unknownProduct.getStatusCode().value()).isEqualTo(404);
    assertThat(unknownDeal.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void ctRtMer03ExcludesUnlistedProductsFromListedOnlyReads() {
    String runId = UUID.randomUUID().toString();
    HttpHeaders merchantHeaders = serviceHeaders();
    merchantHeaders.set("X-Merchant-Id", "1");
    MerchantStore.Product created = http.exchange("/internal/v1/merchants/1/products", HttpMethod.POST,
      new HttpEntity<>(new MerchantStore.ProductRequest(null, "CT 下架商品 " + runId, "契约测试", 1999, 3, true), merchantHeaders), MerchantStore.Product.class).getBody();
    try {
      MerchantStore.Product unlisted = http.exchange("/internal/v1/merchants/1/products/" + created.id() + "/toggle",
        HttpMethod.POST, new HttpEntity<>(merchantHeaders), MerchantStore.Product.class).getBody();
      assertThat(unlisted.listed()).isFalse();

      MerchantStore.Product[] listedOnly = http.exchange("/internal/v1/merchants/1/products?listedOnly=true",
        HttpMethod.GET, new HttpEntity<>(serviceHeaders()), MerchantStore.Product[].class).getBody();
      MerchantStore.Product[] allProducts = http.exchange("/internal/v1/merchants/1/products",
        HttpMethod.GET, new HttpEntity<>(serviceHeaders()), MerchantStore.Product[].class).getBody();
      assertThat(Arrays.stream(listedOnly).map(MerchantStore.Product::id).toList()).doesNotContain(created.id());
      assertThat(Arrays.stream(allProducts).map(MerchantStore.Product::id).toList()).contains(created.id());
    } finally {
      http.exchange("/internal/v1/merchants/1/products/" + created.id(), HttpMethod.DELETE,
        new HttpEntity<>(merchantHeaders), Void.class);
    }
  }

  @Test
  void ctRtMer06And10RejectInvalidCatalogPayloads() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-Merchant-Id", "1");

    ResponseEntity<String> invalidProduct = http.exchange("/internal/v1/merchants/1/products", HttpMethod.POST,
      new HttpEntity<>(new MerchantStore.ProductRequest(null, "", "", 0, -1, true), headers), String.class);
    ResponseEntity<String> invalidDeal = http.exchange("/internal/v1/merchants/1/deals", HttpMethod.POST,
      new HttpEntity<>(new MerchantStore.DealRequest(null, "", "", 0, -1, true), headers), String.class);

    assertThat(invalidProduct.getStatusCode().value()).isEqualTo(400);
    assertThat(invalidDeal.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void ctRtMer07To12RejectUnknownAndCrossMerchantMutations() {
    HttpHeaders owner = serviceHeaders();
    owner.set("X-Merchant-Id", "1");
    HttpHeaders otherMerchant = serviceHeaders();
    otherMerchant.set("X-Merchant-Id", "2");

    ResponseEntity<String> crossToggleProduct = http.exchange("/internal/v1/merchants/1/products/1001/toggle",
      HttpMethod.POST, new HttpEntity<>(otherMerchant), String.class);
    ResponseEntity<String> crossDeleteProduct = http.exchange("/internal/v1/merchants/1/products/1001",
      HttpMethod.DELETE, new HttpEntity<>(otherMerchant), String.class);
    ResponseEntity<String> unknownToggleProduct = http.exchange("/internal/v1/merchants/1/products/999999999/toggle",
      HttpMethod.POST, new HttpEntity<>(owner), String.class);
    ResponseEntity<String> crossToggleDeal = http.exchange("/internal/v1/merchants/1/deals/1/toggle",
      HttpMethod.POST, new HttpEntity<>(otherMerchant), String.class);
    ResponseEntity<String> unknownDeleteDeal = http.exchange("/internal/v1/merchants/1/deals/999999999",
      HttpMethod.DELETE, new HttpEntity<>(owner), String.class);

    assertThat(crossToggleProduct.getStatusCode().value()).isEqualTo(403);
    assertThat(crossDeleteProduct.getStatusCode().value()).isEqualTo(403);
    assertThat(unknownToggleProduct.getStatusCode().value()).isEqualTo(404);
    assertThat(crossToggleDeal.getStatusCode().value()).isEqualTo(403);
    assertThat(unknownDeleteDeal.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void merchantFavoritesAreIsolatedAndDuplicateWritesConflict() {
    long userId = 92011L;
    HttpHeaders userHeaders = serviceHeaders();
    userHeaders.set("X-User-Id", Long.toString(userId));

    ResponseEntity<Void> added = http.exchange("/internal/v1/users/" + userId + "/favorites/1",
      HttpMethod.POST, new HttpEntity<>(userHeaders), Void.class);
    ResponseEntity<String> duplicate = http.exchange("/internal/v1/users/" + userId + "/favorites/1",
      HttpMethod.POST, new HttpEntity<>(userHeaders), String.class);
    Long[] favorites = http.exchange("/internal/v1/users/" + userId + "/favorites",
      HttpMethod.GET, new HttpEntity<>(userHeaders), Long[].class).getBody();

    HttpHeaders anotherUser = serviceHeaders();
    anotherUser.set("X-User-Id", "92012");
    ResponseEntity<String> crossUser = http.exchange("/internal/v1/users/" + userId + "/favorites",
      HttpMethod.GET, new HttpEntity<>(anotherUser), String.class);
    ResponseEntity<Void> removed = http.exchange("/internal/v1/users/" + userId + "/favorites/1",
      HttpMethod.DELETE, new HttpEntity<>(userHeaders), Void.class);
    ResponseEntity<String> repeatedRemove = http.exchange("/internal/v1/users/" + userId + "/favorites/1",
      HttpMethod.DELETE, new HttpEntity<>(userHeaders), String.class);

    assertThat(added.getStatusCode().value()).isEqualTo(200);
    assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
    assertThat(favorites).contains(1L);
    assertThat(crossUser.getStatusCode().value()).isEqualTo(403);
    assertThat(removed.getStatusCode().value()).isEqualTo(200);
    assertThat(repeatedRemove.getStatusCode().value()).isEqualTo(404);
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
