package com.team2.talkking.global.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF 보안 해제 (JWT 및 비동기 소켓 환경 필수)
            .csrf(csrf -> csrf.disable()) 
            
            // 2. CORS 통합 바인딩 적용
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 🎯 [핵심 추가] 8080 웹소켓 핸드셰이크를 낚아채는 기본 로그인 폼 및 HTTP 인증 완벽 해제!
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            
            // 3. URL별 권한 완전 개방
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/ws",                 // 웹소켓 기본 관문
                    "/ws/**",              // SockJS 스트리밍/폴링 통로 (403 원천 차단)
                    "/index.html", 
                    "/login.html",      
                    "/signup.html",
                    "/main.html",        
                    "/api/users/signup", 
                    "/api/users/login",
                    "/api/chat/**",        // 🎯 [교정] /api/chat/ 하위의 모든 이력, 유저 목록, 읽음 처리, 초대 API 프리패스!
                    "/favicon.ico"
                ).permitAll()
                
                // 그 외 기타 보안이 꼭 필요한 영역만 인증 적용
                .anyRequest().permitAll() // 💡 개발 완료 시점에 .authenticated()로 전환하시는 것을 추천합니다.
            );
            
        return http.build();
    }

    // 🌐 8080 포트 CORS 완전 개방 빈 추가 (소켓 통신 안정화용)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowedMethods(Collections.singletonList("*"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}