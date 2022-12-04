FROM eclipse-temurin:17 as builder
ENV DB_HOST=db \
     DB_PORT=123 \
     DB_USERNAME=username \
     DB_PASSWORD=password \
     DB_NAME=name
WORKDIR /opt/app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline
COPY ./src ./src
RUN ./mvnw clean install

FROM eclipse-temurin:17
WORKDIR /opt/app
EXPOSE 8080
COPY --from=builder /opt/app/target/*.jar /opt/app/*.jar
ENTRYPOINT ["java", "-jar", "/opt/app/*.jar" ]