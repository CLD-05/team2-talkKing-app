# ========================================
# ai-error-analyzer/Dockerfile
# 독립적 Spring Boot 애플리케이션 (프로젝트 외부 모듈)
# ========================================

# ========================================
# 1단계: 의존성 캐싱 (소스 변경과 무관)
# ========================================
FROM maven:3.9-eclipse-temurin-17 AS dependency-cache
WORKDIR /ai-error-analyzer

# ai-error-analyzer만 필요 (독립적!)
COPY ai-error-analyzer/pom.xml .

# 💡 의존성만 미리 받아둠 (이 단계는 소스코드가 바뀌어도 캐시됨)
# -f 옵션으로 직접 pom.xml 지정 (프로젝트 외부 모듈이므로)
RUN mvn dependency:go-offline -B -f pom.xml

# ========================================
# 2단계: 빌드 (소스 컴파일 및 패키징)
# ========================================
FROM dependency-cache AS builder
WORKDIR /ai-error-analyzer

# 💡 ai-error-analyzer 소스만 복사
COPY ai-error-analyzer/ .

# 💡 독립적으로 빌드 (다른 모듈 의존성 없음)
RUN mvn clean package

# ========================================
# 3단계: 실행 이미지 (경량화 Alpine JRE + 타임존 설정)
# ========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 타임존 설정 (이벤트 로깅 타임스탬프 동기화 필수)
RUN apk add --no-cache tzdata \
  && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
  && echo "Asia/Seoul" > /etc/timezone

# 빌드 메타데이터 주입
ARG BUILD_DATE
ARG GIT_SHA
ENV APP_BUILD_DATE=$BUILD_DATE
ENV APP_GIT_SHA=$GIT_SHA

# 완성된 아티팩트만 가져옴
COPY --from=builder /ai-error-analyzer/target/*.jar app.jar

# ai-error-analyzer의 포트: 8082
EXPOSE 8082

# Spring Boot 애플리케이션 가동 스크립트
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
