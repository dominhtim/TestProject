# --- STAGE 1: Build ---
# Must match the JRE in stage 2 - bump both together. See CLAUDE.md.
FROM maven:3-eclipse-temurin-25-alpine AS build

WORKDIR /app

# pom.xml first, so the dependency layer survives source edits.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
# Tests run in CI, not here.
RUN mvn -B package -DskipTests

# --- STAGE 2: Runtime ---
FROM eclipse-temurin:25-jre-alpine AS final

# Non-root, for defence in depth.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app
EXPOSE 8080

ARG JAR_FILE=target/my-spring-app-0.0.1-SNAPSHOT.jar
COPY --from=build --chown=spring:spring /app/${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
