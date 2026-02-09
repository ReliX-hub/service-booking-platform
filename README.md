# Service Booking Platform

Appointment booking backend with basic settlement on completion.

## Milestone 1 Features

- Provider and service management
- Time slot scheduling
- Order booking flow
- Settlement on order completion

## Quick Start

### 1. Start Database

```bash
docker compose up -d
```

### 2. Run Application

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

### 3. Verify

| Endpoint | URL |
|----------|-----|
| Health | http://localhost:8080/actuator/health |
| Swagger | http://localhost:8080/swagger-ui/index.html |
| Providers | http://localhost:8080/api/providers |

## Project Structure

```
src/main/java/com/relix/servicebooking/
├── common/          # Shared components
├── config/          # Configuration
├── user/            # User module
├── provider/        # Provider module
├── service/         # Service module
├── timeslot/        # Time slot module
├── order/           # Order module
├── settlement/      # Settlement module
└── review/          # Review module
```

## Profiles

| Profile | Database | Seed Data |
|---------|----------|-----------|
| dev | localhost:5432 | Yes |
| docker | postgres:5432 | No |
| test | Testcontainers | No |

## Running Tests

```bash
./mvnw test
```

## Tech Stack

- Java 17
- Spring Boot 3.2.5
- PostgreSQL 15
- Flyway
- Testcontainers
- Swagger/OpenAPI
