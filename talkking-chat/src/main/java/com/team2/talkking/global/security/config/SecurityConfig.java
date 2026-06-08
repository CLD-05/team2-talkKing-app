package com.team2.talkking.global.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 🎯 [추가] 비밀번호를 안전하게 해시 암호화해주는 빈 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF 보안 해제 (JWT 환경에서는 세션을 쓰지 않으므로 disable이 정석입니다)
            .csrf(csrf -> csrf.disable()) 
            
            // 2. URL별 권한 설정
            .authorizeHttpRequests(authorize -> authorize
                // 🎯 [수정] 회원가입, 로그인 API 및 화면 관련 정적 파일들은 로그인 없이 인증 프리패스!
            		// 🎯 SecurityConfig.java 설정을 아래와 같이 완전히 열어줍니다.
            		.requestMatchers(
            		    "/ws",                 // 웹소켓 핸드셰이크 기본 엔드포인트
            		    "/ws/**",              // SockJS 하위의 모든 스트리밍/폴링 경로 프리패스 (이 한 줄로 해결됩니다!)
            		    "/index.html", 
            		    "/login.html",      
            		    "/signup.html",
            		    "/main.html",        
            		    "/api/users/signup", 
            		    "/api/users/login",
            		    "/api/chat/rooms/**" 
            		).permitAll()
                
                // 그 외 모든 채팅방 조회나 관리자 기능 등은 무조건 로그인(인증) 필요
                .anyRequest().authenticated()
            );
            
        return http.build();
    }
}