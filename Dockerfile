# Stage 1: Build the application
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy gradle files for caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (trick to cache gradle dependencies layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code and build jar
COPY src src
RUN ./gradlew bootJar --no-daemon

# Stage 2: Create lightweight runtime image
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
