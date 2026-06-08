package com.team2.talkking.global.rabbitmq.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitNotificationConfig {

    public static final String EXCHANGE_NAME = "chat.exchange";
    
    // 알림 앱이 바라볼 전용 Queue 명칭
    public static final String NOTIFICATION_QUEUE_NAME = "notification.queue";

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE_NAME, true);
    }

    /**
     * 🎯 [핵심 바인딩] "user.#" 또는 "user.*" 패턴의 라우팅 키를 가진 메시지는 
     * 전부 이 알림 전용 큐(notification.queue)로 꽂히도록 이정표를 세웁니다.
     */
    @Bean
    public Binding bindingNotification(Queue notificationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(notificationExchange)
                .with("user.#"); // user.5, user.12 등으로 들어오는 모든 푸시 알림 타겟팅
    }
    
    /**
     * 🎯 [핵심] RabbitMQ가 JSON 문자열을 ChatMessageDto 자바 객체로 
     * 매끄럽게 번역(역직렬화)할 수 있도록 도와주는 통역사 빈(Bean)입니다.
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}