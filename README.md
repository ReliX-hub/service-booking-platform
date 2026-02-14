# Service Booking Platform

A Spring Boot backend for service appointment booking, payment, provider workflow, settlement batch, and refund management.

## Core Business Flow

1. Customer registers/logs in and creates an order.
2. Customer pays order.
3. Provider accepts → starts → completes service.
4. System creates settlement record for completed order.
5. Admin can trigger settlement batch processing.

## Features

- JWT authentication (`register/login/refresh/me/logout`)
- Role-based authorization (`CUSTOMER`, `PROVIDER`, `ADMIN`)
- Provider onboarding
  - Auto-create provider profile when registering with `role=PROVIDER`
  - Provider self-service profile API (`POST/PUT /api/providers/profile`)
- Service management and time-slot management
- Order lifecycle and idempotency support
- Payment and refund APIs
- Settlement query APIs and admin batch processing
- Audit logging
- Flyway migrations + seed data
- Swagger/OpenAPI + Actuator health endpoint

## Tech Stack

- Java 17
- Spring Boot 3.2.5
- Spring Security + JWT
- Spring Data JPA (Hibernate)
- PostgreSQL 16 (Docker)
- Flyway
- Swagger/OpenAPI

## Quick Start (Ready-to-use)

### Run everything with Docker Compose

```bash
docker compose up --build
```

After startup:

| Endpoint | URL |
|---|---|
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI | http://localhost:8080/v3/api-docs |

The app reads configuration from environment variables by default, with sensible fallbacks in `application.yml`:

- `SERVER_PORT` (default `8080`)
- `SPRING_DATASOURCE_URL` (default `jdbc:postgresql://localhost:5432/booking`)
- `SPRING_DATASOURCE_USERNAME` (default `booking`)
- `SPRING_DATASOURCE_PASSWORD` (default `booking`)

## Run without Docker (optional)

1) Start PostgreSQL locally (or via `docker compose up db -d`).

2) Run application:

```bash
./mvnw spring-boot:run
```

or

```bash
./mvnw -DskipTests clean package
java -jar target/service-booking-platform-0.0.1-SNAPSHOT.jar
```

## Important API Groups

- Auth: `/api/auth/*`
- Providers: `/api/providers`, `/api/providers/profile`
- Services: `/api/services`
- Time Slots: `/api/time-slots`
- Orders: `/api/orders`
- Provider order operations: `/api/providers/{providerId}/orders/*`
- Payments/Refunds: `/api/orders/{id}/pay`, `/api/refunds`
- Settlements: `/api/settlements`, `/api/admin/settlements/*`

## Project Structure

```text
src/main/java/com/relix/servicebooking/
├── auth/            # Authentication & JWT
├── user/            # User domain
├── provider/        # Provider profile & provider operations
├── service/         # Service catalog
├── timeslot/        # Time slot scheduling
├── order/           # Order lifecycle
├── payment/         # Payment handling
├── refund/          # Refund handling
├── settlement/      # Settlement + batch processing
├── audit/           # Audit logging
└── config/          # Security and app configs
```

## Profiles

| Profile | Purpose |
|---|---|
| `dev` | local development customization |
| `docker` | docker-specific overrides (optional) |
| `test` | Testcontainers integration tests |

## Tests

```bash
./mvnw test
```
