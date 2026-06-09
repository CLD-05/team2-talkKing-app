# ========================================
# talkking-notification/Dockerfile
# 멀티스테이지 빌드 + 캐싱 최적화
# ========================================

# 1단계: 의존성 캐싱 (소스 변경과 무관)
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

# talkking-notification만 빌드
# 테스트도 함께 실행
RUN mvn clean package -pl talkking-notification -am

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

# 완성된 JAR만 가져옴
COPY --from=builder /app/talkking-notification/target/*.jar app.jar

EXPOSE 8081

# Spring Boot 애플리케이션 실행
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
