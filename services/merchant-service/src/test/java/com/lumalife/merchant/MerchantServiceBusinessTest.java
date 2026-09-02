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
  void doesNotExposeAnEmptyConversationAsAnotherMerchantConversation() {
    HttpHeaders userHeaders = serviceHeaders();
    userHeaders.set("X-User-Id", "9001");
    http.exchange("/internal/v1/users/9001/conversations/1/messages", HttpMethod.POST,
      new HttpEntity<>(java.util.Map.of("content", "仅属于商家一的会话", "assistantAnswer", "收到"), userHeaders), MerchantStore.ChatMessage[].class);

    HttpHeaders otherMerchantHeaders = serviceHeaders();
    otherMerchantHeaders.set("X-Merchant-Id", "2");
    ResponseEntity<String> response = http.exchange("/internal/v1/merchants/2/conversations/9001",
      HttpMethod.GET, new HttpEntity<>(otherMerchantHeaders), String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(404);
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

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
