# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM gradle:8.12-jdk21 AS builder

WORKDIR /app

# Copy gradle files first for layer caching
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle/ gradle/

# Copy source only — secrets come in at runtime via env vars, not baked into image
COPY src/ src/

# Build the fat jar (skip tests – tested separately via test script)
RUN gradle bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# Heap tuned for 2 GB droplet: 256 MB min, 512 MB max
# All secrets injected via docker-compose environment — no .env in image
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
