package com.lumalife.merchant;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@ConditionalOnProperty(name = "lumalife.events.broker.enabled", havingValue = "true")
class RabbitMerchantEventBusConfiguration {
  @Bean
  TopicExchange lumalifeEventExchange(@Value("${lumalife.events.broker.exchange:lumalife.events}") String name) {
    return new TopicExchange(name, true, false);
  }

  @Bean
  Queue merchantInventoryEvents(@Value("${lumalife.events.broker.queue:merchant-inventory-events}") String name) {
    return new Queue(name, true);
  }

  @Bean
  Binding merchantInventoryConfirmBinding(@Qualifier("merchantInventoryEvents") Queue merchantInventoryEvents,
                                          TopicExchange lumalifeEventExchange) {
    return BindingBuilder.bind(merchantInventoryEvents).to(lumalifeEventExchange).with("inventory.confirm.requested");
  }

  @Bean
  Binding merchantInventoryReleaseBinding(@Qualifier("merchantInventoryEvents") Queue merchantInventoryEvents,
                                          TopicExchange lumalifeEventExchange) {
    return BindingBuilder.bind(merchantInventoryEvents).to(lumalifeEventExchange).with("inventory.release.requested");
  }

  @Bean
  Queue orderInventoryResults(@Value("${lumalife.events.broker.result-queue:order-inventory-results}") String name) {
    return new Queue(name, true);
  }

  @Bean
  Binding orderInventoryResultsBinding(@Qualifier("orderInventoryResults") Queue orderInventoryResults,
                                       TopicExchange lumalifeEventExchange) {
    return BindingBuilder.bind(orderInventoryResults).to(lumalifeEventExchange).with("inventory.result.*");
  }
}
