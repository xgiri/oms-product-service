# ---- Build stage ------------------------------------------------------
# Uses a Maven base image rather than ./mvnw (unlike oms-main's Dockerfile) —
# this is a brand-new repo with no .mvn/wrapper checked in yet. Run
# `mvn -N wrapper:wrapper` once real repo setup happens and switch this back
# to the mvnw pattern oms-main uses, for the same reproducibility reasons
# explained there.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B dependency:go-offline

# Tests skipped here — same reasoning as oms-main's Dockerfile: they need
# Testcontainers (Docker-in-Docker) and a running Kafka/Postgres/Redis,
# which this build stage doesn't have. Run `mvn test` in CI before this
# image is built, not as part of building it.
COPY src/ src/
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system productservice && useradd --system --gid productservice productservice
USER productservice

COPY --from=build /build/target/*.jar app.jar

ARG GIT_SHA=unknown
ARG APP_VERSION=unknown

LABEL org.opencontainers.image.revision=$GIT_SHA
LABEL org.opencontainers.image.version=$APP_VERSION

ENV APP_GIT_SHA=$GIT_SHA
ENV APP_VERSION=$APP_VERSION

EXPOSE 8080 8081

# Same reasoning as oms-main's Dockerfile — /actuator/health is the one
# endpoint SecurityConfig leaves unauthenticated, on the management port
# (8081), not the main app port.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl --fail http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
