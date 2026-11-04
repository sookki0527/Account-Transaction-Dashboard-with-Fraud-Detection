FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY target/transaction-service-1.0-SNAPSHOT.jar app.jar

EXPOSE 8082


ENTRYPOINT ["java", "-jar", "app.jar"]