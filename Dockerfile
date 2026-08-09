# --- STAGE 1: Build ---
# The JDK here must match the JRE in stage 2. It previously did not (JDK 26
# building for a JRE 25 runtime); pom.xml now uses <release> so the compiler
# would catch it, but bump both stages together regardless.
FROM maven:3-eclipse-temurin-26-alpine AS build

WORKDIR /app

# pom.xml on its own first, so the dependency layer is only invalidated when
# dependencies actually change - not on every source edit.
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
