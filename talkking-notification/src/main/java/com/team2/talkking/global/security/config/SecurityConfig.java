package com.team2.talkking.global.security.config; // 💡 실제 패키지 경로에 맞게 수정

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF 보안 해제 (웹소켓 연결 및 테스트를 위해 필수)
            .csrf(csrf -> csrf.disable())
            
            // 2. CORS 기본 설정 적용 (아래의 corsConfigurationSource 빈 활용)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 🚨 3. [핵심 추가] 스프링 시큐리티 기본 로그인 폼 및 HTTP 기본 인증창 강제 해제!!
            // 이 두 줄이 없으면 /ws-notif 요청이 들어올 때 스프링이 자체 로그인 창으로 리다이렉트 시켜버려서 403이 뜹니다.
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            
            // 4. HTTP 요청 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 🎯 알림 웹소켓 핸드셰이크 경로(/ws-notif/**)는 로그인 없이도 무조건 접근 허용!
                .requestMatchers("/ws-notif/**").permitAll()
                .anyRequest().permitAll() 
            );

        return http.build();
    }

    // 🌐 브라우저 CORS 차단 완벽 방어용 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        configuration.setAllowedOriginPatterns(Collections.singletonList("*")); // 모든 도메인 허용
        configuration.setAllowedMethods(Collections.singletonList("*"));        // 모든 메서드(GET, POST 등) 허용
        configuration.setAllowedHeaders(Collections.singletonList("*"));        // 모든 헤더 허용
        configuration.setAllowCredentials(true);                               // 쿠키/인증 정보 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}