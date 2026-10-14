# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar && cp build/libs/*.jar /app/application.jar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app/application.jar ./application.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
