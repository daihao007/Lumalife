package com.lumalife.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumalife.domain.Models.Order;
import com.lumalife.service.boundary.OrderServicePort;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/** Routes order query/cancel to order-service while preserving an explicit rollback path for unmigrated flows. */
@Configuration
@ConditionalOnProperty(prefix = "lumalife.migration.order", name = {"enabled", "backfill-completed"}, havingValue = "true")
public class RemoteOrderServicePort {
  @Bean
  @Primary
  OrderServicePort remoteOrderPort(DemoStore fallback, ObjectMapper mapper, RestClient.Builder builder,
      @Value("${lumalife.services.order.base-url:http://localhost:8083}") String baseUrl,
      @Value("${lumalife.internal.service-token:}") String token) {
    RestClient client = builder.baseUrl(baseUrl).defaultHeader("X-Internal-Service-Token", token).build();
    var handler = (java.lang.reflect.InvocationHandler) (proxy, method, args) -> {
      if (method.getName().equals("userOrders")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/orders").header("X-User-Id", String.valueOf(user.id())).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Order.class)).toList();
      }
      if (method.getName().equals("cancel")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/{id}/cancel", args[1]).header("X-User-Id", String.valueOf(user.id())).retrieve().body(Map.class);
        return mapper.convertValue(row, Order.class);
      }
      return method.invoke(fallback, args);
    };
    return (OrderServicePort) Proxy.newProxyInstance(OrderServicePort.class.getClassLoader(), new Class[]{OrderServicePort.class}, handler);
  }
}
