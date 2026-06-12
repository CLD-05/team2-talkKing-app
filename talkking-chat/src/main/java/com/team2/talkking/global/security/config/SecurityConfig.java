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
            .csrf(csrf -> csrf.disable()) 
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            
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
                    "/api/chat/**",        
                    "/css/**", "/js/**", "/images/**", "/favicon.ico",
                    "/ws", "/ws/**",
                    "/ws-notif", "/ws-notif/**" // 👈 우리가 추가한 웹소켓 엔드포인트도 안전하게 오픈
                ).permitAll()
                
                .anyRequest().permitAll() // 임시 전체 허용 상태 유지
            );
            
        return http.build();
    }

    // 🌐 실서버 멀티파드 + AWS ALB 완벽 대응 CORS 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // ✅ 수정 핵심: 자격 증명(Cookie, Header)을 무조건 허용해야 세션 고정(Stickiness)이 작동합니다!
        configuration.setAllowCredentials(true);
        
        // ✅ 중요: Credentials가 true일 때는 "*"를 쓸 수 없으므로, 로컬과 AWS 로드밸런서 주소를 명시적으로 적어줍니다.
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:8080",
            "http://localhost:5500", // Live Server용 테스트 주소
            "http://k8s-talkking-talkking-893e5c97f8-1005057240.ap-northeast-2.elb.amazonaws.com" // 👈 실서버 ALB 주소 필수 등록!
        ));
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}