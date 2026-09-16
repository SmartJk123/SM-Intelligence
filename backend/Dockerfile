# syntax=docker/dockerfile:1

# Stage 1: Build the multi-module Maven project
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /workspace

# Copy parent POM and all module POMs for dependency caching
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY identity-service/pom.xml identity-service/
COPY accounts-service/pom.xml accounts-service/
COPY transactions-service/pom.xml transactions-service/
COPY categories-service/pom.xml categories-service/
COPY budgets-service/pom.xml budgets-service/
COPY investments-service/pom.xml investments-service/
COPY forecasts-service/pom.xml forecasts-service/
COPY notifications-service/pom.xml notifications-service/
COPY audit-service/pom.xml audit-service/

# Cache Maven dependencies
RUN mvn dependency:go-offline -B || true

# Copy the full source trees
COPY . .

# Which service to build (passed as build argument: e.g. identity-service)
ARG SERVICE
RUN mvn clean package -pl ${SERVICE} -am -DskipTests

# Stage 2: Production JRE image
FROM eclipse-temurin:25-jre
WORKDIR /app

ARG SERVICE
COPY --from=builder /workspace/${SERVICE}/target/*.jar app.jar

# Run as non-root user
USER 1001:1001

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
