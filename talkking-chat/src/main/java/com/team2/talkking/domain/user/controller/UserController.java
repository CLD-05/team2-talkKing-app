package com.team2.talkking.domain.user.controller;

import com.team2.talkking.domain.user.service.UserService;
import com.team2.talkking.global.jwt.JwtProvider;
import com.team2.talkking.global.jwt.TokenService;
import com.team2.talkking.global.jwt.TokenResponseDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final JwtProvider jwtProvider; // 🎫 토큰에서 가공 정보를 추출하거나 생성할 때 사용
    private final TokenService tokenService; // 💾 Redis 적재용 서비스 추가

    /**
     * 📝 회원가입 엔드포인트
     */
    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody SignUpRequest request) {
        userService.signUp(request.getUsername(), request.getPassword(), request.getNickname());
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    /**
     * 🔐 로그인 엔드포인트 (Access, Refresh 동시 발급 및 Redis 적재)
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        // 1. 기존 로그인 로직을 통과시킵니다. (여기서 예외가 안 나면 인증 성공 유저)
        // 💡 만약 기존 userService.login()이 내부에서 이미 단일 토큰을 만들고 있다면, 
        // 멘토링 점검 단계 이후 필요에 따라 유저 엔티티(or ID) 자체를 반환하도록 고도화하는 것을 추천합니다.
        // 현재는 호환성을 위해 이 토큰에서 유저 정보를 다시 파싱하거나 활용하는 형태로 구성합니다.
        String tempToken = userService.login(request.getUsername(), request.getPassword());
        
        // 2. 인증이 완료된 유저의 정보(ID, Nickname 등)를 기반으로 새로운 토큰 쌍 발급
        Long userId = jwtProvider.getUserId(tempToken);
        String nickname = jwtProvider.getNickname(tempToken);
        
        String accessToken = jwtProvider.createAccessToken(userId, request.getUsername(), nickname);
        String refreshToken = jwtProvider.createRefreshToken(userId);

        // 3. 💾 [가장 중요] 발급된 Refresh Token을 Redis에 보안 TTL 설정과 함께 적재
        tokenService.saveRefreshToken(userId, refreshToken);
        
        // 4. 프론트엔드가 편하게 분기 처리하여 쿠키나 스토리지에 넣을 수 있도록 포맷팅
        Map<String, String> response = new HashMap<>();
        response.put("accessToken", accessToken);  // 기존 "token" 대신 명시적인 이름 부여
        response.put("refreshToken", refreshToken); // 새로 추가된 리프레시 토큰
        
        return ResponseEntity.ok(response);
    }

    /**
     * 🔄 토큰 재발급(Reissue) 엔드포인트 (반환 포맷 규격 일원화 패치)
     */
    @PostMapping("/reissue")
    public ResponseEntity<Map<String, String>> reissue(@RequestBody ReissueRequest requestDto) {
        // 1. 기존 서비스를 호출하여 새로운 토큰 더미 DTO를 획득합니다.
        TokenResponseDto tokenResponseDto = tokenService.reissueTokens(requestDto.getRefreshToken());
        
        // 2. 🎯 [핵심 패치] 프론트엔드 자바스크립트가 안전하게 꺼낼 수 있도록 Map 객체에 명시적인 Key 포맷팅 처리
        Map<String, String> response = new HashMap<>();
        
        // 💡 본인의 TokenResponseDto 내부 getter 메서드명(getAccessToken 등)에 맞게 매핑하세요.
        response.put("accessToken", tokenResponseDto.getAccessToken());   // "accessToken" 키 이름 보장
        response.put("refreshToken", tokenResponseDto.getRefreshToken()); // "refreshToken" 키 이름 보장
        
        return ResponseEntity.ok(response);
    }

    // 💡 통신용 내부 DTO 클래스들
    @Getter
    @NoArgsConstructor
    public static class SignUpRequest {
        private String username;
        private String password;
        private String nickname;
    }

    @Getter
    @NoArgsConstructor
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Getter
    @NoArgsConstructor
    public static class ReissueRequest {
        private String refreshToken;
    }
}