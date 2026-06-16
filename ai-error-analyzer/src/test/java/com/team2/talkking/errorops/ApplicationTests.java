package com.team2.talkking.errorops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    // 1. DB 접속 테스트를 시도하다가 터지는 것을 막기 위해 임시 메모리 H2로 유도 (H2 드라이버가 빌드 환경에 있어야 함)
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",

    // 2. Redis 및 기타 인프라 모듈 에러 방어용 Mock 변수 난사
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "errorops.gemini.api-key=mock-key",
    "errorops.slack.critical-webhook-url=http://localhost",
    "errorops.slack.warning-webhook-url=http://localhost",
    "errorops.slack.info-webhook-url=http://localhost"
})
class ApplicationTests {

    @Test
    void contextLoads() {
        // 모든 인프라 변수의 Mocking이 완벽하면 그제야 정상 패스됩니다.
    }
}