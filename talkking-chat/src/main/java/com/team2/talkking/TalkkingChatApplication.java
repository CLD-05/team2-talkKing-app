package com.team2.talkking; // 🎯 원래 맞았던 패키지 경로 유지

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration; // 🎯 추가
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration; // 🎯 추가
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = {
    SecurityAutoConfiguration.class,           // 🔒 스프링 시큐리티가 임의로 기본 필터를 켜는 것을 방어합니다.
    UserDetailsServiceAutoConfiguration.class  // 🔒 Using generated security password가 생성되는 원인을 원천 차단합니다.
})
@ComponentScan(basePackages = "com.team2.talkking")
@EntityScan(basePackages = "com.team2.talkking")
@EnableJpaRepositories(basePackages = "com.team2.talkking")
public class TalkkingChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalkkingChatApplication.class, args);
    }
}