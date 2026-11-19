
FROM maven:3.9.13-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Download dependencies separately so Docker can cache this layer.
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build the application.
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage: use a smaller Java image without Maven.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/rental-tracker-0.1.0-SNAPSHOT.jar app.jar

# Directory used for the persistent SQLite database.
RUN mkdir -p /data

# Allow the database path to be overridden through DB_PATH.
CMD ["sh", "-c", "exec java -Drentaltracker.db=\"${DB_PATH:-/data/rental-tracker.db}\" -jar app.jar"]
