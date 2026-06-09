package com.team2.talkking.global.redis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.team2.talkking.global.redis.pubsub.RedisSubscriber;

import java.util.List;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:#{null}}")
    private String password;

    @Value("${spring.data.redis.cluster.nodes:#{null}}")
    private List<String> clusterNodes;

    // 채팅 메시지를 주고받을 단일 Redis 토픽(채널) 정의
    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic("chatroom");
    }
    
    // 💬 [통합 및 해결] 두 개로 찢어져서 중복되던 컨테이너 빈을 하나로 합쳤습니다!
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter,
            ChannelTopic chatTopic) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, chatTopic); // 다중 파드 간 Pub/Sub 리스너 등록
        return container;
    }

    // 실제 메시지를 수신해서 웹소켓으로 브로드캐스팅할 실제 서비스 클래스 연동
    @Bean
    public MessageListenerAdapter listenerAdapter(RedisSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "sendMessage");
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // 🚀 1. 운영계(prod) ElastiCache Redis 클러스터 모드 대응
        if (clusterNodes != null && !clusterNodes.isEmpty() && !clusterNodes.get(0).isEmpty()) {
            RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(clusterNodes);
            if (password != null && !password.isEmpty()) {
                clusterConfig.setPassword(password);
            }
            LettuceConnectionFactory factory = new LettuceConnectionFactory(clusterConfig);
            factory.setValidateConnection(false); // 핀포인트 해결책 유지!
            return factory;
        }

        // 🛠️ 2. 개발계(dev) 단일 노드 모드 대응
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isEmpty()) {
            standaloneConfig.setPassword(password);
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standaloneConfig);
        factory.setValidateConnection(false);
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        
        // 가독성 및 직렬화 안정성을 위해 String / JsonSerializer 적용
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        return redisTemplate;
    }
}