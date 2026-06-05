# syntax=docker/dockerfile:1

# --- Build stage: Gradle 9.1 running natively on JDK 25 ---
FROM gradle:9.1.0-jdk25-alpine AS build
WORKDIR /home/gradle/project

# Resolve dependencies first so this layer is cached until the build scripts change.
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true

# Compile and package the application (tests run separately in CI, not in the image build).
COPY --chown=gradle:gradle src ./src
RUN gradle --no-daemon clean bootJar -x test

# --- Runtime stage: slim JRE 25, non-root ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl is used by the container HEALTHCHECK to probe the actuator endpoint.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app && useradd --system --gid app app

COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
USER app

EXPOSE 8080
# Honour container memory limits instead of a fixed -Xmx (good for small paid hosts).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=4s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
