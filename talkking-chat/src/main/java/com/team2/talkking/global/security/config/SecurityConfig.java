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
            
            // 폼 로그인 및 기본 HTTP 인증 비활성화 유지
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            
            // 3. URL별 권한 관리
            .authorizeHttpRequests(authorize -> authorize
                // 로그인 화면과 메인 화면의 각 고유 html 주소 및 정적 리소스를 완전히 열어둡니다.
                // 이렇게 해야 브라우저가 각 화면에 맞는 CSS/JS를 온전하게 독립적으로 로드합니다.
                .requestMatchers(
                    "/", 
                    "/login", 
                    "/signup", 
                    "/login.html",
                    "/main.html",
                    "/signup.html",
                    "/api/users/signup", 
                    "/api/users/login",
                    "/api/chat/**",        // 채팅 API 레이어 허용 유지
                    "/css/**", "/js/**", "/images/**", "/favicon.ico",
                    "/ws", "/ws/**"
                ).permitAll()
                
                .anyRequest().permitAll()
            );
            
        return http.build();
    }

    // 🌐 CORS 완전 개방 빈 설정
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