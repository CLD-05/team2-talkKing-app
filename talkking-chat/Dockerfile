FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY . .
RUN mvn clean package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG BUILD_DATE
ARG GIT_SHA
ENV APP_BUILD_DATE=$BUILD_DATE
ENV APP_GIT_SHA=$GIT_SHA

COPY --from=builder /app/talkking-chat/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
