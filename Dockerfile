FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY target/transaction-service-0.1.0-SNAPSHOT.jar /app/transaction-service.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/transaction-service.jar"]
