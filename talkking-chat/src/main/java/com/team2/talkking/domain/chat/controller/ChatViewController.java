package com.team2.talkking.domain.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatViewController {

    /**
     * 1. 루트 경로 (/) 관문
     * 🔓 프론트엔드가 토큰 유무에 따라 화면 포워딩을 제어할 수 있도록 
     * 우선은 무조건 메인 화면으로 던져줍니다. (보안은 프론트 최상단 엔진이 통제)
     */
    @GetMapping("/")
    public String rootPage() {
        return "redirect:/main"; 
    }

    /**
     * 2. 깔끔한 로그인 주소 관문 (/login)
     */
    @GetMapping("/login")
    public String loginPage() {
        return "forward:/login.html";
    }

    /**
     * 3. 깔끔한 메인 대시보드 주소 관문 (/main)
     * 🎯 [핵심 수정] 자바 서버단에서 토큰을 검사하여 302로 튕겨내던 차단막을 파괴합니다.
     * 이제 묻지도 따지지도 말고 main.html을 브라우저에 포워딩합니다.
     */
    @GetMapping("/main")
    public String mainPage() {
        // 🔓 검증 로직 제거: 브라우저가 main.html을 읽어야만 
        // 엑세스 토큰 유실 시 리프레시 토큰으로 자동 재발급(Reissue)하는 JS가 구동될 수 있습니다.
        return "forward:/main.html";
    }

    /**
     * 4. 회원가입 주소 관문 (/signup)
     */
    @GetMapping("/signup")
    public String signupPage() {
        return "forward:/signup.html";
    }
}