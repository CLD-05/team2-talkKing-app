package com.team2.talkking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // 에러 로그에 찍힌 활성화 프로파일
class TalkkingChatApplicationTests {

    // 🎯 [핵심 추가] 테스트 작동 시 StringRedisTemplate이 없어서 터지는 문제를 가짜 빈으로 방어합니다.
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
        // 스프링 부트 로드 검증 테스트 통과용
    }
}
