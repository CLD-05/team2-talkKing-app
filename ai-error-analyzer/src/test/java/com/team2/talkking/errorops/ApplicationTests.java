package com.team2.talkking.errorops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    // 1. DB 주소 설정 
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=", // 👈 따옴표 마감과 쉼표 구분을 확실하게 해줍니다.
    
    // 2. JPA 설정 (뭉개졌던 부분 분리)
    "spring.jpa.hibernate.ddl-auto=create-drop",

    // 3. Redis 및 기타 인프라 모듈 Mocking (우리가 application.yml에 정의한 대문자 변수명과 매핑)
    "REDIS_HOST=localhost",
    "REDIS_PORT=6379",
    "REDIS_PASSWORD=",
    "ALERT_DATASOURCE_URL=jdbc:h2:mem:testdb;MODE=PostgreSQL",
    "ALERT_DATASOURCE_USERNAME=sa",
    "ALERT_DB_PASSWORD="
})
class ApplicationTests {

    @Test
    void contextLoads() {
        // 프로퍼티 구분이 명확해지면 하이버네이트가 H2 인메모리를 정상적으로 물고 부팅에 성공합니다!
    }
}