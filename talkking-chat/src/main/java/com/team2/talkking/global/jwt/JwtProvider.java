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

    @Value("${jwt.secret:defaultSecretKeyForTalkKingMessengerSystem2026}")
    private String secretKeyString;

    // 🕒 토큰 만료 시간 분리 설정
    private final long accessTokenValidityInMilliseconds = 1000L * 60 * 30;  // 30분
    private final long refreshTokenValidityInMilliseconds = 1000L * 60 * 60 * 24 * 14; // 14일 (2주)

    private SecretKey key;

    @PostConstruct
    protected void init() {
        this.key = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 🎫 1. Access Token 생성 (기존 createToken 메서드명 유지 및 시간 변경)
     * 유저의 식별 정보와 권한 정보(Claims)를 가득 담아 보안 필터에서 활용합니다.
     */
    public String createAccessToken(Long userId, String username, String nickname) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidityInMilliseconds);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("nickname", nickname)
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    /**
     * 🔄 2. Refresh Token 생성 (새로 추가)
     * 유저 정보는 최소화(userId 정도만)하고, 만료 시간만 길게 잡아 토큰 재발급 용도로만 씁니다.
     */
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshTokenValidityInMilliseconds);

        return Jwts.builder()
                .subject(String.valueOf(userId)) // 리프레시 토큰은 주체를 userId로 세팅하면 Redis 조회 시 편리합니다.
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
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
    
    /**
     * 🕒 (선택 추가) Redis 저장 시 편리하도록 리프레시 토큰의 남은 만료 시간(초 단위)을 계산합니다.
     */
    public long getExpirationSeconds(String token) {
        Date expiration = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
        return (expiration.getTime() - System.currentTimeMillis()) / 1000;
    }
}