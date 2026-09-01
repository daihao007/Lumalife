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

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
