package com.team2.talkking.global.redis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
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

    // ❌ 기존 chatTopic(), redisMessageListenerContainer(), listenerAdapter() 빈(Bean)들은 모두 삭제되었습니다.

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // 🚀 1. 운영계(prod) ElastiCache Redis 클러스터 모드 대응
        if (clusterNodes != null && !clusterNodes.isEmpty() && !clusterNodes.get(0).isEmpty()) {
            RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(clusterNodes);
            if (password != null && !password.isEmpty()) {
                clusterConfig.setPassword(password);
            }
            LettuceConnectionFactory factory = new LettuceConnectionFactory(clusterConfig);
            factory.setValidateConnection(false); 
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