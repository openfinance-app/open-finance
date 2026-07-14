# =============================================================================
# Dockerfile — Unified multi-stage build for Open-Finance
# Stage 1: Build React frontend (Node.js + npm)
# Stage 2: Build Spring Boot backend with frontend embedded (Maven / Java 21)
# Stage 3: Minimal JRE runtime image
# =============================================================================

# ── Stage 1: Frontend build ──────────────────────────────────────────────────
FROM node:20-alpine AS frontend-build

WORKDIR /app

# Cache npm install layer separately from the source copy.
COPY openfinance-ui/package.json openfinance-ui/package-lock.json ./
RUN npm ci

# Copy full frontend source and build.
COPY openfinance-ui/ ./
ENV NODE_ENV=production
RUN npm run build
# Output: /app/dist/

# ── Stage 2: Backend build ───────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS backend-build

WORKDIR /app

# Cache Maven dependencies before copying source.
COPY pom.xml ./
RUN mvn dependency:go-offline -B -q

# Copy backend source.
COPY src ./src

# Embed the frontend build as Spring Boot static resources.
COPY --from=frontend-build /app/dist ./src/main/resources/static

# Build the fat JAR (skip tests — tests run separately in CI).
RUN mvn clean package -DskipTests -Pprod -B -q && \
    mv target/open-finance-backend-*.jar target/app.jar

# ── Stage 3: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache wget tzdata

# Non-root user for security.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=backend-build /app/target/app.jar app.jar

# Persistent data directories.
RUN mkdir -p /app/data /app/logs /app/attachments /app/temp/imports /app/backups && \
    chown -R appuser:appgroup /app

USER appuser:appgroup

LABEL org.opencontainers.image.title="Open-Finance"
LABEL org.opencontainers.image.description="Personal wealth management — Spring Boot + React"
LABEL org.opencontainers.image.source="https://github.com/openfinance-app/open-finance"

EXPOSE 8080

# NOTE: railway.toml startCommand duplicates these flags for Railway deployments.
# Keep both in sync when changing JVM tuning parameters.
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
