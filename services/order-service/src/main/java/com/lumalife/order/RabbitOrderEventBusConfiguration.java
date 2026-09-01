package com.lumalife.order;

import org.springframework.amqp.core.TopicExchange;
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
}
