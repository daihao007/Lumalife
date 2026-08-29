package com.lumalife.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MerchantServiceBusinessTest {
  @Autowired private TestRestTemplate http;

  @Test
  void exposesMerchantOwnedCatalog() {
    MerchantStore.Merchant merchant = http.getForObject("/internal/v1/merchants/2", MerchantStore.Merchant.class);
    assertThat(merchant.name()).isEqualTo("晨雾咖啡局");
    MerchantStore.Product[] products = http.getForObject("/internal/v1/merchants/1/products", MerchantStore.Product[].class);
    assertThat(products).isNotEmpty();
  }
}
