# 1단계: 빌드 (루트 pom.xml 기준으로 전체 빌드)
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 루트 pom.xml + 모듈 pom.xml 먼저 캐시
COPY pom.xml .
COPY talkking-chat/pom.xml talkking-chat/
COPY talkking-notification/pom.xml talkking-notification/
RUN mvn dependency:go-offline -B

# 소스 복사 후 chat 모듈만 빌드
COPY talkking-chat/src talkking-chat/src
RUN mvn clean package -pl talkking-chat -am -DskipTests

# 2단계: 실행
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG BUILD_DATE
ARG GIT_SHA
ENV APP_BUILD_DATE=$BUILD_DATE
ENV APP_GIT_SHA=$GIT_SHA

COPY --from=builder /app/talkking-chat/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]