package com.team2.talkking.errorops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "DB_URL=jdbc:postgresql://localhost:5432/test",
    "DB_USERNAME=mock",
    "DB_PASSWORD=mock",
    "ALERT_DATASOURCE_URL=jdbc:postgresql://localhost:5432/test",
    "ALERT_DATASOURCE_USERNAME=mock",
    "ALERT_DB_PASSWORD=mock"
})
class ApplicationTests {

    @Test
    void contextLoads() {
        // 빌드 시 컨텍스트 로딩 테스트가 위 가짜 프로퍼티들 덕분에 정상 패스됩니다.
    }
}