FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY target/Fraud-Detection-Service-1.0-SNAPSHOT.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
