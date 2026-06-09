# 1단계: 빌드 (루트 pom.xml 기준으로 전체 빌드)
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 루트 pom.xml + 모듈 pom.xml 먼저 캐시
COPY pom.xml .
COPY talkking-chat/pom.xml talkking-chat/
COPY talkking-notification/pom.xml talkking-notification/
RUN mvn dependency:go-offline -B

# 모든 소스 복사 (chat과 notification 모두)
COPY talkking-chat/src talkking-chat/src
COPY talkking-notification/src talkking-notification/src

# notification 모듈만 빌드 (의존성 자동 처리)
RUN mvn clean package -pl talkking-notification -am

# 2단계: 실행
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG BUILD_DATE
ARG GIT_SHA
ENV APP_BUILD_DATE=$BUILD_DATE
ENV APP_GIT_SHA=$GIT_SHA

COPY --from=builder /app/talkking-notification/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
