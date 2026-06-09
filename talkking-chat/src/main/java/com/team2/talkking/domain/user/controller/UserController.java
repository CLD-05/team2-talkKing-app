package com.team2.talkking.domain.user.controller;

import com.team2.talkking.domain.user.service.UserService;
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

    /**
     * 📝 회원가입 엔드포인트
     */
    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody SignUpRequest request) {
        userService.signUp(request.getUsername(), request.getPassword(), request.getNickname());
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    /**
     * 🔐 로그인 엔드포인트 (JWT 발급 후 JSON Body로 반환)
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        String token = userService.login(request.getUsername(), request.getPassword());
        
        // 프론트엔드가 자바스크립트 LocalStorage 등에 쉽게 저장할 수 있도록 Map 객체에 담아 JSON으로 응답
        Map<String, String> response = new HashMap<>();
        
        // 🎯 [수정] append -> put 으로 변경하여 맵에 토큰을 태웁니다.
        response.put("token", token); 
        
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
}