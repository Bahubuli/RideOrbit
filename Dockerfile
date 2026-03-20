# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM gradle:8.12-jdk21 AS builder

WORKDIR /app

# Copy gradle files first for layer caching
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle/ gradle/

# Copy source + env
COPY src/ src/
COPY .env .env

# Build the fat jar (skip tests – tested separately via test script)
RUN gradle bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy jar and env from builder
COPY --from=builder /app/build/libs/*.jar app.jar
COPY --from=builder /app/.env .env

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
