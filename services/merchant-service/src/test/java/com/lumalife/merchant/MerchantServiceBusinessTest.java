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
  void requiresServiceAndMerchantIdentityForCatalogWrites() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-Merchant-Id", "2");
    ResponseEntity<MerchantStore.Product> response = http.exchange("/internal/v1/merchants/1/products", HttpMethod.POST,
      new HttpEntity<>(new MerchantStore.ProductRequest(null, "越权商品", "", 100, 1, true), headers), MerchantStore.Product.class);
    assertThat(response.getStatusCode().value()).isEqualTo(403);
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
