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
            
            // 📌 JWT 인증을 위한 Session Creation Policy를 STATELESS로 설정하는 것이 좋습니다.
            // (세션을 서버에 생성하지 않고 토큰으로만 인증하기 위함)
            .sessionManagement(session -> session
                .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            )
            
            // 3. URL별 권한 관리
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/", 
                    "/login", 
                    "/signup", 
                    "/login.html",
                    "/main.html",
                    "/signup.html",
                    "/api/users/signup", 
                    "/api/users/login",
                    "/api/users/reissue", // 🔥 [추가] 리프레시 토큰을 통한 토큰 재발급 API는 인증 없이 접근 가능해야 합니다.
                    "/api/chat/**",        // 채팅 API 레이어 허용 유지
                    "/css/**", "/js/**", "/images/**", "/favicon.ico",
                    "/ws", "/ws/**",
                    "/ws-notif", "/ws-notif/**"
                ).permitAll()
                
                // 💡 추후 인증이 필요한 다른 API들을 보호하려면 아래를 .authenticated()로 바꾸고 
                // JwtAuthenticationFilter를 .addFilterBefore()로 등록하게 됩니다.
                .anyRequest().permitAll() 
            );
            
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 1. 모든 오리진 패턴 허용 (이렇게 해두면 인프라 내부 사설 IP 포워딩 환경에서도 안 터집니다)
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        
        // 2. 🔥 [가장 중요] 무조건 true로 유지해야 다중 파드 환경에서 세션 고정 쿠키가 작동합니다!
        configuration.setAllowCredentials(true); 
        
        // 모든 HTTP 메서드 허용
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // 모든 헤더 허용
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        
        // 캐시 시간
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}