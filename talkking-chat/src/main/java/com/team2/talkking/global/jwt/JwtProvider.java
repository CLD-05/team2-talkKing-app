package com.team2.talkking.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    // 💡 application.yml에 등록해서 쓸 비밀키 (최소 32글자 이상의 임의의 문자열)
    @Value("${jwt.secret:defaultSecretKeyForTalkKingMessengerSystem2026}")
    private String secretKeyString;

    // 토큰 만료 시간 (기본 1일 = 24시간)
    private final long tokenValidityInMilliseconds = 1000L * 60 * 60 * 24;

    private SecretKey key;

    @PostConstruct
    protected void init() {
        // 비밀키 문자열을 복호화 알고리즘에 쓸 SecretKey 객체로 변환
        this.key = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 🎫 유저 정보를 기반으로 JWT 토큰을 생성합니다.
     */
    /**
     * 🎫 유저 정보를 기반으로 JWT 토큰을 생성합니다.
     */
    public String createToken(Long userId, String username, String nickname) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + tokenValidityInMilliseconds);

        // 🎯 [핵심] 기존의 Jwts.claims().build() 인스턴스를 거치지 않고 빌더에 다이렉트로 주입합니다.
        return Jwts.builder()
                .subject(username)           // 토큰의 식별자 아이디 명시
                .claim("userId", userId)     // 큐 저장용 유저 고유 PK 주입
                .claim("nickname", nickname) // 화면 표시용 닉네임 주입
                .issuedAt(now)               // 발행 시간 설정
                .expiration(validity)        // 만료 시간 설정
                .signWith(key)               // 암호화 서명 키 주입
                .compact();                  // 최종 문자열 압축 발행
    }

    /**
     * 🔍 토큰이 유효한지 검증합니다. (변조 여부, 만료 여부 확인)
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 🔑 토큰 내부에 숨겨진 유저 고유 ID(PK)를 꺼냅니다.
     */
    public Long getUserId(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userId", Long.class);
    }

    /**
     * 🏷️ 토큰 내부에 숨겨진 유저 닉네임을 꺼냅니다.
     */
    public String getNickname(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("nickname", String.class);
    }
}