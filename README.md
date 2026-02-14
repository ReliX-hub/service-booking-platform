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
- Flyway migrations + dev seed data
- Swagger/OpenAPI + Actuator health endpoint

## Tech Stack

- Java 17
- Spring Boot 3.2.5
- Spring Security + JWT
- Spring Data JPA (Hibernate)
- PostgreSQL 15
- Flyway
- Swagger/OpenAPI

## Quick Start

### 1) Start PostgreSQL

```bash
docker compose up -d
```

Default local DB expected by application config:

- Host: `localhost`
- Port: `5432`
- DB: `booking`
- User: `booking`
- Password: `booking123`

### 2) Run Application

#### Option A: Dev profile

```bash
# run with dev profile
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# or run packaged jar (uses application.yml datasource defaults unless env overrides)
java -jar target/service-booking-platform-0.0.1-SNAPSHOT.jar
```

#### Option B: Packaged JAR (default profile)

```bash
java -jar target/service-booking-platform-0.0.1-SNAPSHOT.jar
```

> You can override datasource via env vars:
>
> - `SPRING_DATASOURCE_URL`
> - `SPRING_DATASOURCE_USERNAME`
> - `SPRING_DATASOURCE_PASSWORD`

### 3) Verify

| Endpoint | URL |
|---|---|
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI | http://localhost:8080/v3/api-docs |

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
├── common/          # Shared DTO/exception/entity
└── config/          # Security and app configs
```

## Profiles

| Profile | Database | Seed Data |
|---|---|---|
| `dev` | local PostgreSQL (`localhost:5432`) | Yes |
| `docker` | docker network PostgreSQL (`postgres:5432`) | No |
| `test` | Testcontainers | No |

## Tests

```bash
./mvnw test
```

## Notes for Contributors

- If port `8080` is occupied, run with `--server.port=8081`.
- For local E2E, use API-only flow (avoid direct DB updates) to validate real business path.
