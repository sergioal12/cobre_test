# ==============================================================================
# Stage 1: Dependency Cache & Build (Java 21 + Maven 3.9)
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Cache Maven dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and seed file
COPY src ./src
COPY notification_events.json ./notification_events.json

# Package application JAR
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Test Runner (Optional target: docker build --target test .)
# ==============================================================================
FROM build AS test
CMD ["mvn", "test", "-B"]

# ==============================================================================
# Stage 3: Production Runtime (Eclipse Temurin 21 JRE Alpine, Non-Root User)
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Install curl for container healthcheck and create non-root user
RUN apk add --no-cache curl && \
    addgroup -S cobre && \
    adduser -S cobre -G cobre

# Copy seed data and compiled artifact
COPY --from=build --chown=cobre:cobre /workspace/notification_events.json /app/notification_events.json
COPY --from=build --chown=cobre:cobre /workspace/target/*.jar /app/app.jar

USER cobre

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=25s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

