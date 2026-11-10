# Multi-stage build for Spring Boot application (Java 25)
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

# Copy gradle wrapper and definitions first for better layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN ./gradlew dependencies --no-daemon || true

# Copy application source and build executable bootJar
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

COPY --from=builder /workspace/build/libs/minidoodle-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
