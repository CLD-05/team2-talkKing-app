# ========================================
# talkking-notification/Dockerfile (최적화 + 타임존)
# ========================================

# ========================================
# 1단계: 의존성 캐싱
# ========================================
FROM maven:3.9-eclipse-temurin-17 AS dependency-cache
WORKDIR /app

# 부모 및 서브 모듈 pom.xml만 먼저 복사
COPY pom.xml .
COPY talkking-chat/pom.xml ./talkking-chat/
COPY talkking-notification/pom.xml ./talkking-notification/

# talkking-notification 모듈 기준 필요한 의존성만 다운로드
RUN mvn dependency:go-offline -B -pl talkking-notification -am

# ========================================
# 2단계: 빌드 (소스 컴파일 및 패키징)
# ========================================
FROM dependency-cache AS builder
WORKDIR /app

# 전체 소스 복사
COPY . .

# talkking-notification과 의존 모듈만 빌드 (테스트 포함)
RUN mvn clean package -pl talkking-notification -am

# ========================================
# 3단계: 실행 이미지 (경량화 Alpine JRE + 타임존 설정)
# ========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 타임존 설정 (메신저 타임스탬프 동기화 필수)
RUN apk add --no-cache tzdata \
  && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
  && echo "Asia/Seoul" > /etc/timezone

# 빌드 메타데이터 주입
ARG BUILD_DATE
ARG GIT_SHA
ENV APP_BUILD_DATE=$BUILD_DATE
ENV APP_GIT_SHA=$GIT_SHA

# 완성된 아티팩트만 이관
COPY --from=builder /app/talkking-notification/target/*.jar app.jar

EXPOSE 8081

# Spring Boot 애플리케이션 가동 (타임존 설정)
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
