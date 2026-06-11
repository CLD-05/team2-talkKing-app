package com.team2.talkking.domain.chat.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatViewController {

    /**
     * 1. 루트 경로 (/) 관문
     * 쿠키를 검사하여 토큰이 없으면 주소창을 '/login'으로 리다이렉트 시키고,
     * 토큰이 유효하게 있으면 주소창을 '/main'으로 확실하게 리다이렉트합니다.
     */
    @GetMapping("/")
    public String rootPage(HttpServletRequest request) {
        boolean hasToken = false;

        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie != null && cookie.getName() != null && "accessToken".equals(cookie.getName())) {
                    if (cookie.getValue() != null && !cookie.getValue().trim().isEmpty()) {
                        hasToken = true;
                        break;
                    }
                }
            }
        }

        // 주소창을 깔끔한 URL 경로로 강제 이동시킵니다.
        if (!hasToken) {
            return "redirect:/login";
        }

        return "redirect:/main"; 
    }

    /**
     * 2. 깔끔한 로그인 주소 관문 (/login)
     * 🎯 주소창에는 무조건 http://localhost:8080/login 만 표시됩니다.
     * 내부적으로는 forward를 통해 static/login.html의 보라색 디자인을 그대로 서빙합니다.
     */
    @GetMapping("/login")
    public String loginPage(HttpServletRequest request) {
        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie != null && cookie.getName() != null && "accessToken".equals(cookie.getName())) {
                    if (cookie.getValue() != null && !cookie.getValue().trim().isEmpty()) {
                        return "redirect:/main"; // 이미 로그인되어 있다면 /main으로 튕김
                    }
                }
            }
        }
        return "forward:/login.html";
    }

    /**
     * 3. 깔끔한 메인 대시보드 주소 관문 (/main)
     * 🎯 주소창에는 무조건 http://localhost:8080/main 만 표시됩니다.
     * 내부적으로는 forward를 통해 static/main.html의 대시보드 화면과 웹소켓 기능을 띄웁니다.
     */
    @GetMapping("/main")
    public String mainPage(HttpServletRequest request) {
        boolean hasToken = false;

        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie != null && cookie.getName() != null && "accessToken".equals(cookie.getName())) {
                    if (cookie.getValue() != null && !cookie.getValue().trim().isEmpty()) {
                        hasToken = true;
                        break;
                    }
                }
            }
        }

        if (!hasToken) {
            return "redirect:/login"; // 토큰 없으면 로그인 주소로 축출
        }

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