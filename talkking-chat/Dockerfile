# ========================================
# talkking-chat/Dockerfile
# 멀티스테이지 빌드 + 캐싱 최적화
# ========================================

# 1단계: 의존성 캐싱 (소스 변경과 무관)
# pom.xml만 먼저 복사하고 의존성 다운로드
# 이후 소스 변경이 있어도 이 레이어는 캐시됨
FROM maven:3.9-eclipse-temurin-17 AS dependency-cache
WORKDIR /app

# pom.xml만 먼저 복사 (의존성 고정)
COPY pom.xml .
COPY talkking-chat/pom.xml talkking-chat/
COPY talkking-notification/pom.xml talkking-notification/

# 의존성 미리 다운로드 (캐싱됨)
RUN mvn dependency:go-offline -B

# ========================================
# 2단계: 빌드 (소스 컴파일 및 테스트)
# ========================================
FROM dependency-cache AS builder
WORKDIR /app

# 전체 소스 복사 (의존성은 이미 다운로드됨)
COPY . .

# talkking-chat만 빌드 (-pl: 선택 모듈, -am: 의존성 자동 빌드)
RUN mvn clean package -pl talkking-chat -am

# ========================================
# 3단계: 실행 이미지 (가벼운 Alpine JRE)
# ========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드 메타데이터
ARG BUILD_DATE
ARG GIT_SHA
ENV APP_BUILD_DATE=$BUILD_DATE
ENV APP_GIT_SHA=$GIT_SHA

# 완성된 JAR만 가져옴 (의존성은 JAR에 포함됨)
COPY --from=builder /app/talkking-chat/target/*.jar app.jar

EXPOSE 8080

# Spring Boot 애플리케이션 실행
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
