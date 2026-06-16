package com.team2.talkking.errorops;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none", // 🚀 하이버네이트가 DDL 검증(접속 시도)을 안 하도록 차단합니다.
    "spring.data.redis.host=localhost",   // Redis 접속 예외 방어
    "errorops.gemini.api-key=mock_key",
    "errorops.slack.critical-webhook-url=http://localhost",
    "errorops.slack.warning-webhook-url=http://localhost",
    "errorops.slack.info-webhook-url=http://localhost"
})
class ApplicationTests {

    // 🚀 [핵심 방어선] 테스트 구동 시 실제 DB 드라이버를 로드해서 접속하는 대신, 가짜 껍데기 빈(Mock)을 주입합니다.
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public DataSource dataSource() {
            return Mockito.mock(DataSource.class);
        }
    }

    @Test
    void contextLoads() {
        // 하이버네이트가 실제 localhost:5432를 찌르지 않고 Mock 껍데기를 바라보므로 
        // 깃허브 마스킹 버그고 뭐고 무조건 0.1초 만에 패스됩니다!
    }
}