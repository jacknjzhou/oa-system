FROM eclipse-temurin:21-jre-alpine
LABEL authors="oa-system"
WORKDIR /app
COPY target/oa-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar", "--spring.profiles.active=prod"]
