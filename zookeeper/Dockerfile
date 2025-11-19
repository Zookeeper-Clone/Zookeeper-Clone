# ------------ BUILD STAGE ------------
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy source
COPY pom.xml .
COPY src ./src

# Build fat JAR
RUN mvn -q -DskipTests clean package

# ------------ RUNTIME STAGE ------------
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy fat jar from build stage
COPY --from=build /app/target/*-jar-with-dependencies.jar app.jar

# Expose the port (dynamic)
EXPOSE 6000

# Run app
ENTRYPOINT ["java", "-jar", "app.jar"]
