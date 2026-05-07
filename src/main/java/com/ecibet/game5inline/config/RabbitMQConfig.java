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

    public static final String GAME_EVENTS_EXCHANGE = "game.events.exchange";
    public static final String BET_WON_QUEUE = "bet.won.queue";
    public static final String BET_LOST_QUEUE = "bet.lost.queue";
    public static final String BET_WON_ROUTING_KEY = "bet.won";
    public static final String BET_LOST_ROUTING_KEY = "bet.lost";

    @Bean
    public TopicExchange gameEventsExchange() {
        return new TopicExchange(GAME_EVENTS_EXCHANGE);
    }

    @Bean
    public Queue betWonQueue() {
        return new Queue(BET_WON_QUEUE, true);
    }

    @Bean
    public Queue betLostQueue() {
        return new Queue(BET_LOST_QUEUE, true);
    }

    @Bean
    public Binding betWonBinding() {
        return BindingBuilder
                .bind(betWonQueue())
                .to(gameEventsExchange())
                .with(BET_WON_ROUTING_KEY);
    }

    @Bean
    public Binding betLostBinding() {
        return BindingBuilder
                .bind(betLostQueue())
                .to(gameEventsExchange())
                .with(BET_LOST_ROUTING_KEY);
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