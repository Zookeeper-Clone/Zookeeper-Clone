# Use a lightweight JRE image
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the pre-built fat JAR
COPY target/zookeeper-1.0-SNAPSHOT-shaded.jar app.jar

# Expose the port
EXPOSE 6000

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
