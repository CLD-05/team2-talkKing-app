# ========================================
# talkking-chat/Dockerfile (최적화 + 타임존 + FIS 도구 내장)
# 멀티스테이지 빌드 + 의존성 캐싱 + 타임존 설정
# ========================================

# ========================================
# 1단계: 의존성 캐싱 (소스 변경과 무관)
# ========================================
FROM maven:3.9-eclipse-temurin-17 AS dependency-cache
WORKDIR /app

COPY pom.xml .
COPY talkking-chat/pom.xml ./talkking-chat/
COPY talkking-notification/pom.xml ./talkking-notification/

RUN mvn dependency:go-offline -B -pl talkking-chat -am

# ========================================
# 2단계: 빌드 (소스 컴파일 및 패키징)
# ========================================
FROM dependency-cache AS builder
WORKDIR /app

COPY . .

RUN mvn clean package -pl talkking-chat -am

# ========================================
# 3단계: 실행 이미지 (경량화 Alpine JRE + 타임존 설정 + 카오스 테스트 도구)
# ========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 💡 타임존 설정 및 FIS 카오스 테스트를 위한 stress-ng 유틸리티 상시 탑재
RUN apk add --no-cache tzdata stress-ng \
  && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
  && echo "Asia/Seoul" > /etc/timezone

# 빌드 메타데이터 주입
ARG BUILD_DATE
ARG GIT_SHA
ENV APP_BUILD_DATE=$BUILD_DATE
ENV APP_GIT_SHA=$GIT_SHA

# 완성된 아티팩트만 콤팩트하게 이관
COPY --from=builder /app/talkking-chat/target/*.jar app.jar

EXPOSE 8080

# Spring Boot 애플리케이션 가동 스크립트
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]