package com.team2.talkking; // 💡 본인의 알림 서버 메인 패키지 경로

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan; // 🎯 임포트 추가

@SpringBootApplication
// 🎯 [치트키] global 패키지에 있는 SecurityConfig와 WebSocketConfig를 무조건 긁어오도록 스캔 범위를 강제 지정합니다.
@ComponentScan(basePackages = {"com.team2.talkking"}) 
public class TalkkingNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalkkingNotificationApplication.class, args);
    }
}