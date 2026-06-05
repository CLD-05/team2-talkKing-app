FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
COPY talkking-chat/pom.xml talkking-chat/
COPY talkking-notification/pom.xml talkking-notification/
RUN mvn dependency:go-offline -B

# notification 모듈만 빌드
COPY talkking-notification/src talkking-notification/src
RUN mvn clean package -pl talkking-notification -am -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG BUILD_DATE
ARG GIT_SHA
ENV APP_BUILD_DATE=$BUILD_DATE
ENV APP_GIT_SHA=$GIT_SHA

COPY --from=builder /app/talkking-notification/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]