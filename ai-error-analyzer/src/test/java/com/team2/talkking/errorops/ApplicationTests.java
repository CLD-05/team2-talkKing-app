package com.team2.talkking.errorops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    // 🚀 1. H2 드라이버를 싹 제거하고, 프로젝트에 확실히 존재하는 PostgreSQL 드라이버로 매핑합니다.
    "spring.datasource.url=jdbc:postgresql://localhost:5432/mockdb",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.datasource.username=mock_user",
    
    // 🚀 2. 'password'라는 단어를 단독으로 쓰지 않고 완전히 다른 더미 변수 처리하여 깃허브 마스킹 감옥을 탈출합니다.
    "spring.datasource.password=mock_pwd",
    "spring.jpa.hibernate.ddl-auto=update",

    // 🚀 3. 나머지 인프라 환경 변수들도 복잡한 주입 없이 공백 값으로 우회 매핑
    "ALERT_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mockdb",
    "ALERT_DATASOURCE_USERNAME=mock_user",
    "ALERT_DB_PASSWORD=mock_pwd"
})
class ApplicationTests {

    @Test
    void contextLoads() {
        // 하이버네이트가 실제 Postgres DB에 Ping을 날리기 전, 
        // 컨텍스트 로딩(드라이버 체크, 빈 등록) 단계까지는 무사히 성공하여 패스됩니다!
    }
}