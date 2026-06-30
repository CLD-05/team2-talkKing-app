package com.team2.talkking.errorops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class ApplicationTests {

    @Test
    void contextLoads() {
        // application-test.yml의 H2 설정을 자동으로 사용
    }
}