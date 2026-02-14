# -------- build stage --------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Cache Maven wrapper and dependencies separately for faster rebuilds
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q || true

# Copy source and build
COPY src src
RUN ./mvnw -q -DskipTests clean package

# -------- run stage --------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/service-booking-platform-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
