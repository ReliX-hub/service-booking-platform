# -------- build stage --------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY . .
RUN ./mvnw -q -DskipTests clean package

# -------- run stage --------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/service-booking-platform-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
