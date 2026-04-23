package com.ecibet.game5inline.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String GAME_EVENTS_EXCHANGE = "game.events";
    public static final String WALLET_SETTLEMENTS_QUEUE = "wallet.settlements";
    public static final String GAME_SETTLED_ROUTING_KEY = "game.5inline.settled";

    @Bean
    public TopicExchange gameEventsExchange() {
        return new TopicExchange(GAME_EVENTS_EXCHANGE);
    }

    @Bean
    public Queue walletSettlementsQueue() {
        return new Queue(WALLET_SETTLEMENTS_QUEUE, true);
    }

    @Bean
    public Binding walletSettlementsBinding() {
        return BindingBuilder
                .bind(walletSettlementsQueue())
                .to(gameEventsExchange())
                .with(GAME_SETTLED_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}