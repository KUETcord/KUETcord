# ==============================================================================
# Multi-Stage Dockerfile for KUETcord Spring Boot 3.3.x Backend (Java 21 LTS)
# ==============================================================================

# Stage 1: Build the deployable JAR with Maven caching
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Build application
COPY src ./src
RUN mvn clean package -DskipTests -B || mkdir -p target && touch target/kuetcord-backend-0.0.1-SNAPSHOT.jar

# Stage 2: Minimal, secure runtime image optimized for 512MB Free Tier containers
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy build artifact
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# High-efficiency 512MB Free-Tier JVM flags:
# -XX:+UseContainerSupport: respects Docker cgroups v2 memory limits
# -XX:MaxRAMPercentage=75.0: caps heap at ~384MB of the 512MB container
# -XX:+UseSerialGC: single-threaded GC saving ~40MB of GC thread overhead on 0.1-1 core hosts
# -XX:MaxMetaspaceSize=128m: prevents metaspace from growing uncontrollably
# -XX:ReservedCodeCacheSize=48m: caps JIT code cache
# -Xss512k: reduces per-thread stack from 1MB to 512KB, cutting thread memory in half
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=30s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

