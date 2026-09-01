package com.lumalife.order;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "lumalife.events.broker.enabled", havingValue = "true")
class RabbitOrderEventBusConfiguration {
  @Bean
  TopicExchange lumalifeEventExchange(@Value("${lumalife.events.broker.exchange:lumalife.events}") String name) {
    return new TopicExchange(name, true, false);
  }

  @Bean
  Queue orderInventoryResults(@Value("${lumalife.events.broker.result-queue:order-inventory-results}") String name) {
    return new Queue(name, true);
  }

  @Bean
  Binding orderInventoryResultsBinding(Queue orderInventoryResults, TopicExchange lumalifeEventExchange) {
    return BindingBuilder.bind(orderInventoryResults).to(lumalifeEventExchange).with("inventory.result.*");
  }
}
