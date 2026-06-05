package com.team2.talkking.global.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF 보안 해제 (테스트 환경)
            .csrf(csrf -> csrf.disable()) 
            
            // 2. URL별 권한 설정 구역 찾기
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/ws/**", "/index.html").permitAll() // 🎯 여기 포트폴리오에 "/test.html"을 추가해 줍니다!
                .anyRequest().authenticated()
            );
            
        return http.build();
    }
}