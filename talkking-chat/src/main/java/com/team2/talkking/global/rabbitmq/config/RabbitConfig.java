package com.team2.talkking.global.rabbitmq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_NAME = "chat.exchange";
    
    // 💾 1. DB 저장 전용 큐 인프라
    public static final String DB_QUEUE_NAME = "chat.db.queue";
    public static final String ROUTING_KEY_DB = "room.*";

    // 🔔 2. 실시간 알림 전용 큐 인프라 [추가]
    public static final String NOTIFICATION_QUEUE_NAME = "notification.queue";
    public static final String ROUTING_KEY_NOTIF = "user.*"; // user.1, user.2 등을 가로챌 와일드카드

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    // ----------------------------------------------------
    // 💾 DB 저장용 큐 & 바인딩 설정
    // ----------------------------------------------------
    @Bean
    public Queue chatDbQueue() {
        return new Queue(DB_QUEUE_NAME, true);
    }

    @Bean
    public Binding bindingDbSave(Queue chatDbQueue, TopicExchange chatExchange) {
        return BindingBuilder
                .bind(chatDbQueue)
                .to(chatExchange)
                .with(ROUTING_KEY_DB);
    }

    // ----------------------------------------------------
    // 🔔 실시간 알림용 큐 & 바인딩 설정 [추가]
    // ----------------------------------------------------
    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE_NAME, true);
    }

    @Bean
    public Binding bindingNotification(Queue notificationQueue, TopicExchange chatExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(chatExchange)
                .with(ROUTING_KEY_NOTIF); // "user.*" 패턴으로 연결 고리 셋업!
    }

    // ----------------------------------------------------
    // 💡 공통 컨버터 및 템플릿 설정
    // ----------------------------------------------------
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}