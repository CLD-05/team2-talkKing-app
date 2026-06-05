package com.team2.talkking.global.redis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
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

    // 💬 다중 파드 간 메시지 동기화를 위한 Pub/Sub 컨테이너 설정
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
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