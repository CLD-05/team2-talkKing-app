package com.team2.talkking.global.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtProvider jwtProvider;

    private static final String REFRESH_TOKEN_PREFIX = "RT:";

    /**
     * 💾 로그인 성공 시 리프레시 토큰을 Redis에 저장합니다.
     */
    public void saveRefreshToken(Long userId, String refreshToken) {
        long expirationSeconds = jwtProvider.getExpirationSeconds(refreshToken);
        
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                expirationSeconds,
                TimeUnit.SECONDS
        );
    }

    /**
     * 🔄 RTR (Refresh Token Rotation) 기반 토큰 재발급 핵심 로직
     */
    public TokenResponseDto reissueTokens(String targetRefreshToken) {
        // 1. 토큰 자체의 유효성(위변조, 만료) 1차 검증
        if (!jwtProvider.validateToken(targetRefreshToken)) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 리프레시 토큰입니다.");
        }

        // 2. 토큰에서 유저 고유 ID 추출
        Long userId = jwtProvider.getUserId(targetRefreshToken);

        // 3. 🎯 [보안 핵심 - RTR 선검증] Redis에 저장된 현재 진짜 토큰을 가져옴
        String savedRefreshToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);

        // 4. Redis에 토큰이 없거나, 클라이언트가 들고 온 토큰과 일치하지 않는 경우 (탈취 및 재사용 시도)
        if (savedRefreshToken == null || !savedRefreshToken.equals(targetRefreshToken)) {
            // 탈취 징후로 판단하여 유저의 다른 보안 피해를 막기 위해 Redis 토큰을 즉시 강제 삭제 (로그아웃 처리)
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
            log.warn("🚨 리프레시 토큰 재사용 및 탈취 의심 감지! 유저 ID: {} 의 토큰을 강제 무효화합니다.", userId);
            throw new IllegalStateException("보안 위협이 감지되었습니다. 다시 로그인해주세요.");
        }

        // 5. 🔍 새로운 토큰 세트 쌍 발급을 위한 유저 정보 가공 (예시로 필요한 정보 주입)
        // 닉네임과 유저네임은 실제 환경에 맞게 회원 엔티티나 Context에서 조회하여 넣어주시면 됩니다.
        String username = "user_" + userId; 
        String nickname = jwtProvider.getNickname(targetRefreshToken); // JwtProvider에 닉네임 파싱이 있다면 활용

        String newAccessToken = jwtProvider.createAccessToken(userId, username, nickname);
        String newRefreshToken = jwtProvider.createRefreshToken(userId);

        // 6. 🔄 [RTR 교체] 새 리프레시 토큰을 Redis에 덮어쓰기 하여 기존 토큰 무효화
        saveRefreshToken(userId, newRefreshToken);

        return new TokenResponseDto(newAccessToken, newRefreshToken);
    }
}