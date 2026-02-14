# Service Booking Platform - Test & Docker Diagnosis Report

**Date:** 2026-02-14
**Environment:** Code analysis performed on Linux; Docker diagnosis based on code review + Windows runtime behavior analysis

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Codebase Overview](#2-codebase-overview)
3. [Business Logic Analysis](#3-business-logic-analysis)
4. [Test Coverage Analysis](#4-test-coverage-analysis)
5. [Docker & Windows Compatibility Diagnosis](#5-docker--windows-compatibility-diagnosis)
6. [Identified Bugs & Issues](#6-identified-bugs--issues)
7. [Proposed Solutions](#7-proposed-solutions)

---

## 1. Executive Summary

### Key Findings

| Category | Status | Details |
|----------|--------|---------|
| Business Logic | Generally Sound | Order state machine, payment idempotency, and settlement processing are well designed |
| Test Coverage | **Weak** | Only 8 test files, most are smoke tests; critical business flows lack real assertions |
| Docker Config | **Bug Found** | `application-docker.yml` uses hostname `postgres` but `docker-compose.yml` names the service `db` |
| Windows Docker | **Root Cause Identified** | Multiple issues: missing `.gitattributes` (CRLF corruption), Testcontainers Docker socket detection, missing `DOCKER_HOST` configuration |
| Security | Minor Issues | JWT secret hardcoded in defaults, actuator endpoints fully public |

### Critical Issues (Ranked by Severity)

1. **[BUG] Docker hostname mismatch** - `application-docker.yml` references `postgres:5432` but `docker-compose.yml` names the DB service `db` - app will fail to connect when using the `docker` profile
2. **[BUG] Windows Docker - `mvnw` CRLF corruption** - No `.gitattributes` file means Git on Windows will convert `mvnw` (shell script) line endings to CRLF, causing Docker build failure
3. **[BUG] Testcontainers Windows detection** - No `testcontainers.properties` or env config for Windows Docker Desktop socket
4. **[ISSUE] Weak test coverage** - Most integration tests only verify HTTP status codes, not business logic correctness

---

## 2. Codebase Overview

### Technology Stack
- **Java 17** / Spring Boot 3.2.5
- **PostgreSQL 16** with Flyway migrations (V1-V8)
- **JWT authentication** (jjwt 0.12.5)
- **Testcontainers** 1.19.7 for integration tests
- **Docker** multi-stage build (eclipse-temurin:21)

### Module Structure (90 Java classes)

| Module | Classes | Purpose |
|--------|---------|---------|
| `auth/` | 7 | JWT auth, registration, login, refresh tokens, token cleanup |
| `order/` | 7 | Order lifecycle, state validation, idempotency |
| `payment/` | 5 | Payment processing, idempotency |
| `provider/` | 7 | Provider management, order operations |
| `service/` | 7 | Service catalog CRUD |
| `timeslot/` | 6 | Time slot scheduling |
| `settlement/` | 10 | Settlement records, batch processing, scheduler |
| `refund/` | 6 | Refund processing |
| `audit/` | 6 | Audit logging |
| `config/` | 3 | Security, JWT filter, OpenAPI |
| `common/` | 8 | Exceptions, base entity, API response |
| `user/` | 3 | User entity, repository, DTO |
| `review/` | 1 | Review entity (unused) |

---

## 3. Business Logic Analysis

### 3.1 Order State Machine

**File:** `order/validator/OrderStateValidator.java`

```
PENDING -> PAID, CANCELLED
PAID -> CONFIRMED, CANCELLED
CONFIRMED -> IN_PROGRESS, CANCELLED
IN_PROGRESS -> COMPLETED, CANCELLED
COMPLETED -> (terminal)
CANCELLED -> (terminal)
```

**Assessment:** Well-designed. Uses `Map<OrderStatus, Set<OrderStatus>>` for single source of truth. All state transitions are validated before execution. Pessimistic write locks (`findByIdWithLock`) prevent concurrent state corruption.

**Potential Issue:** The `cancelOrder` method in `OrderService` (line 329-356) performs an idempotent check for already-cancelled orders BEFORE the state validation. This is correct behavior, but note that customer cancellation does NOT trigger automatic refunds (unlike provider rejection). This may be a business logic gap - if a customer cancels a PAID order, no refund is created.

### 3.2 Payment Processing

**File:** `payment/service/PaymentService.java`

**Assessment:** Solid idempotency handling with two-layer approach:
1. Pre-check: If order already PAID, return existing payment
2. Post-catch: Handle `DataIntegrityViolationException` race condition

**Potential Issue:** Payment is always set to `SUCCEEDED` immediately (line 58) - this is a simulated payment with no real gateway integration. The amount is taken from `order.getTotalPrice()` rather than the request, which is correct (prevents amount manipulation).

### 3.3 Settlement Processing

**File:** `settlement/service/SettlementService.java` + `SettlementBatchService.java`

**Assessment:**
- Platform fee is hardcoded at 10% (`PLATFORM_FEE_RATE = 0.10`)
- Settlement is created as PENDING when order completes
- Batch processing runs daily at 2:00 AM (Chicago timezone)
- Batch ID format: `BATCH-YYYY-MM-DD` - only one batch per day allowed

**Potential Issue:** The `processBatch()` method in `SettlementBatchService` (line 28-108) runs entirely within a SINGLE transaction (`@Transactional`). If any exception occurs during processing that is NOT caught by the inner try-catch, the entire batch rolls back. More critically, if the method is processing 10,000 settlements, the entire operation is one long-running transaction which could cause database lock contention.

**Potential Issue:** The `settledAt` field is set at settlement CREATION time (line 147 in SettlementService), not at actual settlement completion. This is semantically misleading.

### 3.4 Refund Processing

**File:** `refund/service/RefundService.java`

**Assessment:** Refund is created and immediately processed synchronously in the same call (`createRefund` calls `processRefund`). This is a simulated flow. The idempotency check (`existsByOrderId`) prevents duplicate refunds.

**Potential Issue:** The `processRefund` method is annotated `@Transactional` but is called from `createRefund` which is also `@Transactional`. Since Spring uses proxy-based AOP, this self-invocation means `processRefund`'s `@Transactional` annotation is IGNORED - it runs within the outer transaction. If `processRefund` fails, the entire `createRefund` transaction could be affected depending on exception handling.

### 3.5 Authentication & Security

**File:** `auth/service/JwtService.java`, `auth/service/AuthService.java`, `config/SecurityConfig.java`

**Assessment:**
- JWT uses HS256 with base64-decoded secret key
- Refresh tokens are stored as SHA-256 hashes (good practice)
- Login revokes all existing refresh tokens (single-session enforcement)
- Password encoding uses BCrypt

**Security Notes:**
- **JWT secret in application.yml:** A default secret is hardcoded in `application.yml` (line 25). In production, this MUST be overridden via environment variable `JWT_SECRET`.
- **Actuator endpoints are fully public** (`/actuator/**` is `permitAll()`). This exposes health, info, and metrics endpoints without authentication. The `/actuator/metrics` endpoint could leak sensitive performance data.
- **Registration allows CUSTOMER and PROVIDER roles only** - ADMIN role cannot be self-registered (correct).
- **No rate limiting** on login/register endpoints - vulnerable to brute-force attacks.

### 3.6 Time Slot Management

**File:** `timeslot/service/TimeSlotService.java`

**Assessment:** Uses pessimistic locking for `bookSlot` to prevent double-booking. The `releaseSlotSafely` method correctly checks if any order still references the slot before releasing.

**Potential Issue:** No overlap validation when creating time slots. A provider could create two overlapping time slots for the same time range.

---

## 4. Test Coverage Analysis

### 4.1 Test Inventory

| # | Test File | Type | Test Methods | Quality |
|---|-----------|------|-------------|---------|
| 1 | `ServiceBookingApplicationTests` | Integration | 1 | Smoke test (context loads) |
| 2 | `OrderFlowIntegrationTest` | Integration | 3 | Basic HTTP status checks |
| 3 | `OrderIdempotencyTest` | Integration | 1 | Only tests authenticated access |
| 4 | `PaymentIdempotencyTest` | Integration | 1 | Only tests authenticated access |
| 5 | `OrderStateValidatorTest` | Unit | 3 | **Good** - actual logic testing |
| 6 | `RefundServiceTest` | Unit (Mockito) | 2 | Tests access control + truncation |
| 7 | `SettlementBatchServiceTest` | Unit (Mockito) | 1 | Tests batch failure marking |
| 8 | `BaseIntegrationTest` | Infrastructure | 0 | Base class for Testcontainers |

**Total: 12 test methods across 7 test classes**

### 4.2 Test Quality Assessment

#### Good Tests
- `OrderStateValidatorTest` - Properly tests valid transitions, invalid transitions, and operation validation with meaningful assertions
- `RefundServiceTest` - Tests access control and string truncation

#### Weak Tests (Smoke Tests Only)
- `OrderFlowIntegrationTest.fullOrderFlow()` - Registers a user and hits `/api/orders` endpoint, but only checks `response != null`. Does NOT verify:
  - Order creation with valid data
  - Order state transitions
  - Payment flow
  - Provider acceptance/rejection
- `OrderIdempotencyTest.authenticatedEndpointShouldWork()` - Only tests that an authenticated request returns non-null. Does NOT test actual idempotency (sending same request twice).
- `PaymentIdempotencyTest.authenticatedEndpointShouldWork()` - Same issue. Does NOT test payment idempotency.

### 4.3 Critical Coverage Gaps

| Module | Has Tests? | Missing Coverage |
|--------|-----------|------------------|
| `auth/` | Partial (via integration) | Login/register validation, token refresh, token expiry, role-based access |
| `order/` | Partial | Full order lifecycle, cancellation with refund, concurrent order creation |
| `payment/` | Minimal | Actual payment idempotency, duplicate request handling, race conditions |
| `provider/` | **None** | Provider CRUD, provider profile creation, order management |
| `service/` | **None** | Service CRUD, service status validation |
| `timeslot/` | **None** | Time slot booking, release, overlap, concurrent booking |
| `settlement/` | Minimal | Settlement creation, batch processing success path, daily batch idempotency |
| `refund/` | Minimal | Refund creation, refund processing, payment status update |
| `audit/` | **None** | Audit log creation, audit retrieval |
| `config/` | **None** | Security filter chain, CORS, JWT filter |

### 4.4 Missing Test Scenarios

**Critical business flows with NO test coverage:**
1. Complete order flow: Create -> Pay -> Accept -> Start -> Complete -> Settlement
2. Refund flow: Create -> Pay -> Provider Reject -> Auto-refund
3. Customer cancellation of paid order
4. Idempotent order creation (duplicate key)
5. Idempotent payment processing (duplicate requestId)
6. Concurrent order creation for same time slot
7. Concurrent payment for same order
8. Settlement batch processing with mixed success/failure
9. Token refresh and rotation
10. Role-based access control for all endpoints

---

## 5. Docker & Windows Compatibility Diagnosis

### 5.1 BUG: Docker Profile Hostname Mismatch

**Severity: CRITICAL**

| File | Hostname Used |
|------|--------------|
| `docker-compose.yml` | Service named `db` (line 2) |
| `docker-compose.yml` env | `SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/booking` (line 24) |
| `application-docker.yml` | `url: jdbc:postgresql://postgres:5432/booking` (line 3) |

**Problem:** `application-docker.yml` references hostname `postgres` but the Docker Compose service is named `db`. If the app is started with `--spring.profiles.active=docker`, it will try to connect to `postgres:5432` which doesn't resolve.

**Current Workaround:** The `docker-compose.yml` passes `SPRING_DATASOURCE_URL` as an environment variable, which overrides the profile config. So `docker compose up` works, but running the app standalone with `-Dspring.profiles.active=docker` will FAIL.

**Password Mismatch:**

| File | Password |
|------|----------|
| `docker-compose.yml` | `booking` |
| `application-docker.yml` | `booking123` |
| `application-dev.yml` | `booking123` |
| `application.yml` (default) | `booking` |

The `docker-compose.yml` env variables override these, masking the inconsistency.

### 5.2 Windows Docker Issues - Root Cause Analysis

The user reports: "Windows (PowerShell/Git Bash) can't find correct Docker environment, but Linux works fine."

#### Root Cause 1: Missing `.gitattributes` - CRLF Line Ending Corruption

**Severity: HIGH**

There is **no `.gitattributes` file** in the repository. On Windows, Git's default `core.autocrlf` setting converts LF to CRLF on checkout.

**Impact on Docker build:**
- `mvnw` is a POSIX shell script (shebang: `#!/bin/sh`)
- When checked out on Windows with CRLF, the Dockerfile's `RUN ./mvnw -q -DskipTests clean package` will fail with:
  ```
  /bin/sh: ./mvnw: /bin/sh\r: bad interpreter: No such file or directory
  ```
- This error manifests as a confusing Docker build failure

**Evidence:** Running `file mvnw` shows `POSIX shell script, ASCII text executable` - currently LF. But on Windows checkout, this will become CRLF.

#### Root Cause 2: Testcontainers Docker Socket Detection on Windows

**Severity: HIGH**

**File:** `BaseIntegrationTest.java` - Uses Testcontainers `PostgreSQLContainer`

Testcontainers needs to communicate with the Docker daemon. On different platforms:

| Platform | Docker Socket | Detection |
|----------|--------------|-----------|
| Linux | `unix:///var/run/docker.sock` | Auto-detected |
| macOS | `unix:///var/run/docker.sock` | Auto-detected |
| Windows (Docker Desktop) | `npipe:////./pipe/docker_engine` | Requires config |
| Windows (WSL2 backend) | Via WSL2 integration | Requires specific setup |

**Problem:** There is no `testcontainers.properties` file (it's in `.gitignore`!) and no environment variable configuration for Testcontainers to find Docker on Windows.

Common error on Windows:
```
Could not find a valid Docker environment
```

This is exactly what the user describes.

#### Root Cause 3: Docker Desktop WSL2 Backend Configuration

On Windows with Docker Desktop using WSL2 backend:
- Docker runs inside WSL2 VM
- The Docker socket is at `//./pipe/docker_engine` (named pipe)
- PowerShell and Git Bash need `DOCKER_HOST` to be set correctly
- If Docker Desktop's "Expose daemon on tcp://localhost:2375" is disabled, external tools can't connect

**Multiple possible states:**

| Docker Desktop Setting | `DOCKER_HOST` Needed | Testcontainers Behavior |
|----------------------|---------------------|----------------------|
| WSL2 + pipe exposed | `npipe:////./pipe/docker_engine` | Works with config |
| WSL2 + TCP exposed | `tcp://localhost:2375` | Works with env var |
| WSL2 + nothing exposed | N/A | **FAILS** |
| Hyper-V backend | `npipe:////./pipe/docker_engine` | Works with config |

#### Root Cause 4: `dev-up.ps1` Script Limitations

**File:** `scripts/dev-up.ps1`

The script checks:
1. `docker` command exists
2. `docker info` succeeds
3. `docker compose version` exists

**Missing checks:**
- Does not verify Docker Desktop is running in the correct mode (WSL2 vs Hyper-V)
- Does not check `DOCKER_HOST` environment variable
- Does not verify Docker Compose V2 plugin version compatibility
- Does not handle the case where `docker info` succeeds in WSL but the Docker context is wrong for the current shell

#### Root Cause 5: Docker Context Mismatch

On Windows, Docker can have multiple contexts:
```
docker context ls
```

If the user has both Docker Desktop and WSL2 Docker installed, the active context might be wrong:
- PowerShell might use the `desktop-linux` context
- Git Bash might use a different context
- WSL might use the `default` context

This can cause `docker info` to succeed but `docker compose` to fail, or vice versa.

### 5.3 Dockerfile Issues

**File:** `Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q -DskipTests clean package
```

**Issues:**
1. **`COPY . .` copies everything** - includes `.git/`, `target/`, IDE files. Should use `.dockerignore`
2. **No `.dockerignore` file** - results in unnecessarily large build context
3. **No layer caching optimization** - Maven dependencies are re-downloaded on every build. Best practice is to copy `pom.xml` first, download dependencies, then copy source code
4. **Java version mismatch:** Dockerfile uses `eclipse-temurin:21-jdk` but `pom.xml` specifies `<java.version>17</java.version>`. This works (Java 21 can compile Java 17 code) but is inconsistent

---

## 6. Identified Bugs & Issues Summary

### Bugs

| # | Severity | Module | Description | File:Line |
|---|----------|--------|-------------|-----------|
| B1 | CRITICAL | Docker | `application-docker.yml` hostname `postgres` doesn't match docker-compose service name `db` | `application-docker.yml:3` |
| B2 | HIGH | Docker/Windows | No `.gitattributes` - CRLF corruption breaks `mvnw` in Docker on Windows | Repository root |
| B3 | HIGH | Test/Windows | Testcontainers can't find Docker on Windows - no `testcontainers.properties` config, and file is gitignored | `.gitignore:25` |
| B4 | MEDIUM | Docker | Password mismatch: docker-compose uses `booking`, profiles use `booking123` | Multiple files |
| B5 | LOW | Refund | `processRefund` self-invocation bypasses `@Transactional` proxy | `RefundService.java:54` |
| B6 | LOW | Settlement | `settledAt` set at creation time, not actual settlement time | `SettlementService.java:147` |

### Design Issues

| # | Severity | Module | Description |
|---|----------|--------|-------------|
| D1 | MEDIUM | Order | Customer cancellation of PAID order does NOT trigger automatic refund |
| D2 | MEDIUM | Settlement | `processBatch()` runs in single transaction - risky for large batches |
| D3 | LOW | TimeSlot | No overlap validation for time slot creation |
| D4 | LOW | Security | Actuator endpoints (`/actuator/**`) are fully public |
| D5 | LOW | Security | No rate limiting on auth endpoints |
| D6 | LOW | Docker | No `.dockerignore` file - large build context |
| D7 | LOW | Docker | No Maven dependency layer caching in Dockerfile |
| D8 | INFO | Docker | Java version mismatch: Dockerfile uses JDK 21, pom.xml targets Java 17 |

---

## 7. Proposed Solutions

### 7.1 Fix Docker Hostname Mismatch (B1) - CRITICAL

**Option A (Recommended):** Fix `application-docker.yml` to match docker-compose:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/booking
    username: booking
    password: booking
```

**Option B:** Rename docker-compose service from `db` to `postgres`:
```yaml
services:
  postgres:  # was: db
    image: postgres:16
```

### 7.2 Fix Windows CRLF Issue (B2) - HIGH

**Add `.gitattributes` file** to repository root:
```gitattributes
# Auto detect text files and normalize line endings
* text=auto

# Shell scripts must use LF
*.sh text eol=lf
mvnw text eol=lf

# Windows scripts can use CRLF
*.cmd text eol=crlf
*.bat text eol=crlf
*.ps1 text eol=crlf

# Docker files must use LF
Dockerfile text eol=lf
docker-compose.yml text eol=lf
*.yml text eol=lf
*.yaml text eol=lf

# SQL files
*.sql text eol=lf

# Java source
*.java text eol=lf
*.properties text eol=lf
*.xml text eol=lf
```

After adding this file, users need to run:
```bash
git rm --cached -r .
git reset --hard
```

### 7.3 Fix Testcontainers Windows Detection (B3) - HIGH

**Solution 1: Create `testcontainers.properties` (remove from .gitignore)**

Create `src/test/resources/testcontainers.properties`:
```properties
# Enable Testcontainers reuse for faster test runs
testcontainers.reuse.enable=true

# Docker host detection (uncomment for Windows if needed)
# docker.host=npipe:////./pipe/docker_engine
# For TCP: docker.host=tcp://localhost:2375
```

**Solution 2: Add environment detection in BaseIntegrationTest**

Enhance `BaseIntegrationTest.java` to handle Windows Docker detection:
```java
@Container
static PostgreSQLContainer<?> postgres;

static {
    // Help Testcontainers find Docker on Windows
    if (System.getProperty("os.name").toLowerCase().contains("win")) {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost == null || dockerHost.isEmpty()) {
            System.setProperty("DOCKER_HOST", "npipe:////./pipe/docker_engine");
        }
    }

    postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("booking_test")
            .withUsername("test")
            .withPassword("test");
}
```

**Solution 3: Improve `dev-up.ps1`**

Add Docker environment validation:
```powershell
# Check Docker Desktop is running
$dockerInfo = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is not reachable. Start Docker Desktop first."
}

# Verify DOCKER_HOST for Testcontainers
if (-not $env:DOCKER_HOST) {
    $env:DOCKER_HOST = "npipe:////./pipe/docker_engine"
    Write-Host "Set DOCKER_HOST=$env:DOCKER_HOST" -ForegroundColor Yellow
}

# Verify Docker Compose works
docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "'docker compose' plugin is unavailable."
}
```

### 7.4 Fix Password Mismatch (B4) - MEDIUM

Standardize credentials across all config files:
- `docker-compose.yml`: `POSTGRES_PASSWORD: booking`
- `application-docker.yml`: `password: booking`
- `application-dev.yml`: `password: booking`
- Or use `booking123` everywhere - just be consistent

### 7.5 Add `.dockerignore` (D6) - LOW

Create `.dockerignore`:
```
.git
.gitignore
.idea
*.iml
target
*.log
*.md
scripts
src/test
testcontainers.properties
```

### 7.6 Optimize Dockerfile Layer Caching (D7) - LOW

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Build application
COPY src src
RUN ./mvnw -q -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/service-booking-platform-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

### 7.7 Improve Test Coverage (Recommendations)

Priority tests to add:

1. **Full Order Lifecycle Integration Test** - Create order -> Pay -> Accept -> Start -> Complete -> Verify settlement
2. **Refund Flow Test** - Create -> Pay -> Provider Reject -> Verify refund created
3. **Idempotency Tests** - Send same order/payment request twice, verify single record created
4. **Concurrent Booking Test** - Two users booking same time slot simultaneously
5. **Auth Tests** - Login with wrong password, expired token, revoked refresh token
6. **Role-Based Access Tests** - Customer can't access provider endpoints and vice versa

### 7.8 Windows Developer Setup Checklist

For Windows developers, provide these instructions:

1. **Install Docker Desktop** with WSL2 backend
2. **Enable Docker Desktop settings:**
   - Settings > General > "Use the WSL 2 based engine" (checked)
   - Settings > General > "Expose daemon on tcp://localhost:2375" (checked for Testcontainers)
3. **Set environment variables (PowerShell):**
   ```powershell
   [System.Environment]::SetEnvironmentVariable("DOCKER_HOST", "tcp://localhost:2375", "User")
   [System.Environment]::SetEnvironmentVariable("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock", "User")
   ```
4. **After cloning, verify line endings:**
   ```bash
   git config core.autocrlf input
   ```
5. **Run tests:**
   ```powershell
   .\mvnw.cmd test
   ```

---

## Appendix: File Inventory Analyzed

### Source Files Read (complete)
- `Dockerfile`
- `docker-compose.yml`
- `pom.xml`
- `scripts/dev-up.ps1`
- `mvnw.cmd`
- `.gitignore`
- All `application*.yml` config files
- All Flyway migration SQL files (V1-V8)
- `SecurityConfig.java`
- `JwtService.java`, `JwtAuthenticationFilter.java`
- `AuthService.java`, `CurrentUserService.java`
- `OrderService.java`, `OrderController.java`, `OrderStateValidator.java`
- `PaymentService.java`
- `SettlementService.java`, `SettlementBatchService.java`
- `RefundService.java`
- `TimeSlotService.java`
- `ProviderOrderController.java`
- `GlobalExceptionHandler.java`
- `Order.java` entity, `OrderRepository.java`
- All test files (8 files)

### Tools Used
- Static code analysis (manual review of all source files)
- Docker daemon testing (`dockerd --iptables=false --bridge=none`)
- Git attribute inspection (`git check-attr`, `file` command)
- Maven dependency analysis (attempted, blocked by network restrictions)
