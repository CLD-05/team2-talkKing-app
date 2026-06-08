package com.team2.talkking; // 🎯 본인의 메인 패키지 경로

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan; // 🎯 추가
import org.springframework.data.jpa.repository.config.EnableJpaRepositories; // 🎯 추가

@SpringBootApplication
@EntityScan(basePackages = "com.team2.talkking") // 🎯 하이버네이트에게 엔티티가 있는 최상위 경로를 강제로 지정합니다!
@EnableJpaRepositories(basePackages = "com.team2.talkking") // 🎯 레포지토리 경로도 확실하게 집어넣어 줍니다!
public class TalkkingChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalkkingChatApplication.class, args);
    }
}