package com.jcaa.notificationservice.entrypoint.event.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitNotificationConfiguration {

  @Bean
  Queue notificationQueue(final RabbitProperties properties) {
    return QueueBuilder.durable(properties.notification().queue()).build();
  }

  @Bean
  TopicExchange domainEventsExchange(final RabbitProperties properties) {
    return new TopicExchange(properties.domain().exchange(), true, false);
  }

  @Bean
  Binding notificationBinding(
      final Queue notificationQueue,
      final TopicExchange domainEventsExchange,
      final RabbitProperties properties) {
    return BindingBuilder.bind(notificationQueue)
        .to(domainEventsExchange)
        .with(properties.notification().routingKey());
  }

  @Bean
  MessageConverter rabbitMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      final ConnectionFactory connectionFactory, final MessageConverter rabbitMessageConverter) {
    final SimpleRabbitListenerContainerFactory factory =
        new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(rabbitMessageConverter);
    return factory;
  }
}
