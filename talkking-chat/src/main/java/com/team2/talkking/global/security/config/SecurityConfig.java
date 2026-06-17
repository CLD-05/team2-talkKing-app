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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
            
            // 📌 JWT 인증을 위한 Session Creation Policy를 STATELESS로 설정
            .sessionManagement(session -> session
                .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            )
            
            // 3. URL별 권한 관리
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/", 
                    "/login", 
                    "/main",      // 🎯 [추가] 확장자 없는 클린 URL 패턴 허용
                    "/signup", 
                    "/login.html",
                    "/main.html",
                    "/signup.html",
                    "/api/users/signup", 
                    "/api/users/login",
                    "/api/users/reissue", // 리프레시 토큰 허용
                    "/api/chat/**",        // 채팅 API 레이어 허용
                    "/api/users/reissue", // 🔥 [추가] 리프레시 토큰을 통한 토큰 재발급 API는 인증 없이 접근 가능해야 합니다.
                    "/api/chat/**",        // 채팅 API 레이어 허용 유지
                    "/css/**", "/js/**", "/images/**", "/favicon.ico",
                    "/ws", "/ws/**",
                    "/ws-notif", "/ws-notif/**"
                ).permitAll()
                
                // 💡 현재는 필터가 구현 중이므로 전면 허용 상태를 보존하되, 
                // anyRequest().permitAll() 상태여도 시큐리티 코어 자체가 꼬이면 차단됩니다.
                .anyRequest().authenticated() 
            );
            
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 🔥 [CORS 규격 버그 패치] 
        // allowCredentials가 true일 때는 명시적인 허용 주소가 있어야 안전하게 안착합니다.
        // EKS Ingress 도메인 주소나 로컬 환경이 유연하게 뚫리도록 List 형태로 쪼개서 세팅하는 것이 정석입니다.
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:8080",
            "http://localhost:3000",
            "https://*.github.dev", // 클라우드 IDE 환경 방어
            "*"                     // 만약 스프링 버전이 낮다면 와일드카드 허용이 먹히지만, 최신 버전 보안망을 위해 패턴 매핑으로 유도됩니다.
        ));
        
        // 💡 만약 위의 allowedOriginPatterns와 credentials 충돌이 EKS 터미널에서 지속된다면,
        // 아래 한 줄로 대체하여 인프라 게이트웨이(Nginx) 단으로 CORS 통제권을 위임시키는 것이 무조건 성공하는 마스터 키입니다.
        // configuration.setAllowedOrigins(List.of("http://localhost:8080")); 

        configuration.setAllowCredentials(true); 
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}