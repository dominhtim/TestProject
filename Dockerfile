# --- STAGE 1: Build the application ---
# Using a JDK image for compiling and packaging the Spring Boot application
FROM eclipse-temurin:21-jdk-alpine AS build

# Set the working directory
WORKDIR /app

# Copy the Maven wrapper files and the pom.xml for dependency caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies to cache the layers (only if pom.xml changes)
RUN ./mvnw dependency:go-offline

# Copy the rest of the source code
COPY src src

# Build the Spring Boot application, resulting in a JAR file
# Skip tests here as they were run separately in the CI/local development
RUN ./mvnw package -DskipTests

# --- STAGE 2: Create the final, lean runtime image ---
# Using a JRE image for a smaller, more secure runtime environment
FROM eclipse-temurin:21-jre-alpine AS final

# Expose the application port
EXPOSE 8080

# Copy the built JAR file from the 'build' stage
# The JAR file path is standard: /app/target/my-spring-app-0.0.1-SNAPSHOT.jar
ARG JAR_FILE=target/my-spring-app-0.0.1-SNAPSHOT.jar
COPY --from=build /app/${JAR_FILE} app.jar

# Run the Spring Boot application
# The default Spring Boot port 8080 will be used.
ENTRYPOINT ["java", "-jar", "/app.jar"]