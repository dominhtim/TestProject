# --- STAGE 1: Build the application ---
# Uses an official Maven+Temurin image so the build doesn't depend on a
# Maven wrapper being present in the repo (this project doesn't ship one).
FROM maven:3.9-eclipse-temurin-25-alpine AS build

# Set the working directory
WORKDIR /app

# Copy the pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies to cache the layers (only if pom.xml changes)
RUN mvn -B dependency:go-offline

# Copy the rest of the source code
COPY src src

# Build the Spring Boot application, resulting in a JAR file
# Skip tests here as they were run separately in the CI/local development
RUN mvn -B package -DskipTests

# --- STAGE 2: Create the final, lean runtime image ---
# Using a JRE image for a smaller, more secure runtime environment
FROM eclipse-temurin:25-jre-alpine AS final

# Run as a non-root user for defense in depth
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Set the working directory
WORKDIR /app

# Expose the application port
EXPOSE 8080

# Copy the built JAR file from the 'build' stage
# The JAR file path is standard: /app/target/my-spring-app-0.0.1-SNAPSHOT.jar
ARG JAR_FILE=target/my-spring-app-0.0.1-SNAPSHOT.jar
COPY --from=build --chown=spring:spring /app/${JAR_FILE} app.jar

# Run the Spring Boot application
# The default Spring Boot port 8080 will be used.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
