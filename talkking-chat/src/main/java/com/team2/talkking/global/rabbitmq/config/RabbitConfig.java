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
    
    // 🎯 [추가] 고정적으로 사용할 DB 저장 전용 큐 이름
    public static final String DB_QUEUE_NAME = "chat.db.queue";
    
    // 🎯 [추가] 채팅방 메시지를 가로챌 라우팅 키 패턴 (예: room.* 형식의 모든 메시지)
    public static final String ROUTING_KEY = "room.*";

    // 💡 메시지를 라우팅 키 규칙에 따라 매핑해줄 Topic형 Exchange 등록
    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    // 🎯 [추가] 서버가 켜질 때 고정 크기의 DB 저장용 Queue를 자동으로 생성합니다.
    @Bean
    public Queue chatDbQueue() {
        // durable을 true로 설정해야 RabbitMQ 브로커가 재시작되어도 큐와 쌓여있던 메시지가 소멸하지 않습니다.
        return new Queue(DB_QUEUE_NAME, true);
    }

    // 🎯 [추가] 생성한 DB 큐를 chat.exchange에 "room.*" 라우팅 키로 묶어줍니다.
    @Bean
    public Binding bindingDbSave(Queue chatDbQueue, TopicExchange chatExchange) {
        return BindingBuilder
                .bind(chatDbQueue)
                .to(chatExchange)
                .with(ROUTING_KEY);
    }

    // 💡 DTO 객체를 RabbitMQ가 이해할 수 있는 JSON 바이트로 자동 변환해주는 컨버터
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