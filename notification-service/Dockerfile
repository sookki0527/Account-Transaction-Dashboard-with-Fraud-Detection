FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

COPY target/notification-service-1.0-SNAPSHOT.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
