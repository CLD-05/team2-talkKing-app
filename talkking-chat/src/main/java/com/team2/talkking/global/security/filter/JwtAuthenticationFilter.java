package com.team2.talkking.global.security.filter;

import com.team2.talkking.global.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * 🔐 JWT 인증 필터
 * 
 * 모든 HTTP 요청에 대해 Authorization 헤더에서 JWT 토큰을 추출하고
 * 토큰의 유효성을 검증한 후, SecurityContext에 인증 정보를 설정합니다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1️⃣ Authorization 헤더에서 JWT 토큰 추출
            String token = extractToken(request);

            // 2️⃣ 토큰이 존재하고 유효한지 검증
            if (token != null && jwtProvider.validateToken(token)) {
                // 3️⃣ 토큰에서 userId 추출
                Long userId = jwtProvider.getUserId(token);
                String nickname = jwtProvider.getNickname(token);
                
                // 4️⃣ Authentication 객체 생성 (Spring Security에서 인증된 사용자로 인식)
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userId,           // principal (사용자 식별자)
                        null,             // credentials (비밀번호 - JWT는 불필요)
                        new ArrayList()   // authorities (권한 - 필요시 추가)
                );
                
                // 5️⃣ SecurityContext에 인증 정보 저장
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                log.debug("✅ JWT 인증 성공 - userId: {}, nickname: {}", userId, nickname);
            } else if (token != null) {
                log.warn("⚠️ 유효하지 않은 JWT 토큰");
            }
            
        } catch (Exception e) {
            log.error("❌ JWT 필터 처리 중 오류 발생: {}", e.getMessage());
        }

        // 🔄 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }

    /**
     * 🔍 Authorization 헤더에서 JWT 토큰 추출
     * 
     * 형식: Authorization: Bearer <token>
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring("Bearer ".length());
        }
        
        return null;
    }
}