# ========================================
# talkking-chat/Dockerfile (최적화 + 타임존)
# 멀티스테이지 빌드 + 의존성 캐싱 + 타임존 설정
# ========================================

# ========================================
# 1단계: 의존성 캐싱 (소스 변경과 무관)
# ========================================
# pom.xml만 먼저 복사하고 의존성 다운로드
# 이후 소스 변경이 있어도 이 레이어는 캐시됨
FROM maven:3.9-eclipse-temurin-17 AS dependency-cache
WORKDIR /app

# 부모 및 서브 모듈 pom.xml만 먼저 복사하여 의존성 레이어 캐싱
COPY pom.xml .
COPY talkking-chat/pom.xml ./talkking-chat/
COPY talkking-notification/pom.xml ./talkking-notification/

# talkking-chat 모듈 기준 필요한 의존성만 미리 타겟팅 다운로드
RUN mvn dependency:go-offline -B -pl talkking-chat -am

# ========================================
# 2단계: 빌드 (소스 컴파일 및 패키징)
# ========================================
FROM dependency-cache AS builder
WORKDIR /app

# 전체 소스 복사 (이미 다운로드된 의존성 레이어는 유지됨)
COPY . .

# talkking-chat과 의존 모듈만 컴파일/패키징
# 테스트는 CI/CD 워크플로우 단계에서 검증하므로 포함
RUN mvn clean package -pl talkking-chat -am

# ========================================
# 3단계: 실행 이미지 (경량화 Alpine JRE + 타임존 설정)
# ========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 타임존 설정 (메신저 타임스탬프 동기화 필수)
# Asia/Seoul 타임존을 기본값으로 설정
RUN apk add --no-cache tzdata \
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
# 💡 -Duser.timezone=Asia/Seoul: JVM 레벨에서도 타임존 설정
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
