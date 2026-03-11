### Instructions 

1. this is a java spring boot backend project where i am making RideOrbit clone 
2. the developer wants in depth understaing of the code you will guide him through things. 
3. you will not make code changes directly you will show the code and i will manually write the code for in depth code level understanding 
4. you will act like senior architect or mentor that will guide with what, why, how and step by step changes in file which i will manually type.
5. you will show all the code the developer will then either copy paste or manually type 

---

## Project Overview

**RideOrbit** is a ride-hailing backend (like Uber/Ola) built with:
- **Java 21** + **Spring Boot 3.5.7** (Gradle build)
- **PostgreSQL** for persistent data (drivers, passengers, bookings)
- **Redis** for real-time driver location tracking (geospatial)
- **Lombok** for boilerplate reduction
- **Jakarta Validation** for request validation
- **spring-dotenv** for environment variable management (`.env` file)

Base package: `com.jitendra.RideOrbit`

---

## Project Structure & Layer Responsibilities

```
src/main/java/com/jitendra/RideOrbit/
├── RideOrbitApplication.java        ← Spring Boot entry point
├── config/                          ← Configuration classes (Redis, etc.)
├── controller/                      ← REST controllers (HTTP layer only)
├── dto/                             ← Request/Response DTOs (never expose entities)
├── entity/                          ← JPA entities (DB models)
├── exception/                       ← Custom exceptions + global handler
├── mapper/                          ← Manual mappers (Entity ↔ DTO conversion)
├── repository/                      ← Spring Data JPA repositories
└── service/                         ← Service interfaces + impl/ subfolder
    ├── {Entity}ReadService.java     ← Read-only operations interface
    ├── {Entity}WriteService.java    ← Write operations interface
    ├── {Entity}Service.java         ← Combined interface (extends Read + Write)
    └── impl/
        └── {Entity}ServiceImpl.java ← Implementation
```

---

## Architecture & Design Patterns In Use

### 1. Layered Architecture (strict flow)
```
Controller → Service Interface → ServiceImpl → Repository → Database
                                  ↕
                                Mapper (Entity ↔ DTO)
```
- Controllers NEVER touch repositories or entities directly
- Services return DTOs, never entities
- Repositories return entities

### 2. Interface Segregation Principle (ISP)
Each domain has **3 service interfaces**:
- `{Entity}ReadService` — read-only methods (`findById`, `findAll`, custom queries)
- `{Entity}WriteService` — mutation methods (`create`, `update`, `deleteById`)
- `{Entity}Service` — combines both: `extends {Entity}ReadService, {Entity}WriteService`

The domain-specific interfaces are used directly (not extending any generic base).

### 3. DTO Pattern
- **Request DTOs** (`{Entity}Request`) — used for incoming data, carry validation annotations
- **Response DTOs** (`{Entity}Response`) — used for outgoing data, no validation
- Entities are NEVER exposed to controllers or API consumers

### 4. Manual Mapper Pattern
Each domain has a `{Entity}Mapper` class annotated with `@Component`:
- `toEntity(Request)` — converts request DTO → entity (for creation)
- `toResponse(Entity)` — converts entity → response DTO
- `updateEntity(Entity, Request)` — mutates existing entity fields from request (for updates)

We do NOT use MapStruct. Mappers are manual, simple, and explicit.

### 5. Builder Pattern (via Lombok `@Builder`)
All entities, request DTOs, and response DTOs use `@Builder`. Always use builder pattern for object creation, never raw constructors.

### 6. Constructor Injection (via Lombok `@RequiredArgsConstructor`)
All classes use constructor injection through `@RequiredArgsConstructor` + `private final` fields. NO `@Autowired` annotations anywhere.

---

## Naming Conventions (MUST FOLLOW)

| Thing               | Convention                     | Example                          |
|---------------------|--------------------------------|----------------------------------|
| Package             | lowercase                      | `com.jitendra.RideOrbit`        |
| Entity class        | Singular PascalCase            | `Driver`, `Passenger`, `Booking`|
| DB table name       | Plural snake_case              | `drivers`, `passengers`, `bookings` |
| DTO Request         | `{Entity}Request`              | `DriverRequest`                 |
| DTO Response        | `{Entity}Response`             | `DriverResponse`                |
| Mapper              | `{Entity}Mapper`               | `DriverMapper`                  |
| Repository          | `{Entity}Repository`           | `DriverRepository`              |
| Read Service        | `{Entity}ReadService`          | `DriverReadService`             |
| Write Service       | `{Entity}WriteService`         | `DriverWriteService`            |
| Combined Service    | `{Entity}Service`              | `DriverService`                 |
| Service Impl        | `{Entity}ServiceImpl`          | `DriverServiceImpl`             |
| Controller          | `{Entity}Controller`           | `DriverController`              |
| API base path       | `/api/{entity-plural}`         | `/api/drivers`                  |
| Exception classes   | `{Descriptive}Exception`       | `ResourceNotFoundException`     |

---

## Coding Standards & Best Practices

### Entities
- Always use `@Entity`, `@Table(name = "plural_name")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **NEVER use `@Data` on JPA entities** — it generates `equals()`, `hashCode()`, `toString()` on ALL fields, which force-loads LAZY relationships, causes `LazyInitializationException`, and breaks Hibernate proxy identity
- Use `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with `@EqualsAndHashCode.Include` on the `id` field only
- `@Data` is fine on DTOs — just never on entities
- ID field: `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` with type `Long`
  - Note: `IDENTITY` disables Hibernate batch inserts (each INSERT hits DB individually). For PostgreSQL at scale, `SEQUENCE` strategy is preferred. `IDENTITY` is kept here for simplicity.
- Audit timestamps: `createdAt` (`@Column(nullable = false, updatable = false)`) and `updatedAt` — managed via `@PrePersist` and `@PreUpdate` lifecycle callbacks
- Use `@Builder.Default` for fields with default values (e.g., `isAvailable = true`, `status = PENDING`)
- Enums: use `@Enumerated(EnumType.STRING)` — never ordinal. Define enums in **separate files** (e.g., `entity/BookingStatus.java`), NOT as inner enums inside entity classes — otherwise controllers must import entity classes to reference the enum, breaking layered architecture
- Relationships use `@ManyToOne(fetch = FetchType.LAZY)` — always LAZY fetch

### DTOs
- Request DTOs: use Jakarta Validation annotations (`@NotBlank`, `@NotNull`, `@Email`, `@Positive`, etc.) with custom messages
- Response DTOs: no validation, just data fields
- Both use `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- Use wrapper types (`Long`, `Boolean`) not primitives for nullable fields

### Repositories
- Extend `JpaRepository<Entity, Long>`
- `@Repository` annotation is **optional** — Spring Data auto-detects interfaces extending `JpaRepository`. Harmless to add but not required.
- Use Spring Data derived query methods (e.g., `findByEmail`, `existsByEmail`, `findByIsAvailableTrue`)
- Prefer derived methods. Use `@Query` only when derived method names become unreadable
- **Always** push filtering to the database (e.g., `findByIsAvailableTrue()` instead of `findAll().stream().filter(...)`)

### Services
- `@Service` on implementation class
- `@RequiredArgsConstructor` for injection
- `@Slf4j` for logging (Lombok annotation)
- Class-level `@Transactional(readOnly = true)` (safe default — if you forget to annotate a read method, it defaults to read-only instead of wastefully opening a read-write transaction)
- Override **write methods** with `@Transactional` (read-write) explicitly
- **Exception strategy — use domain-specific exceptions, NEVER `IllegalArgumentException`:**
  - `ResourceNotFoundException` → 404 (entity not found) — already exists
  - `DuplicateResourceException` → 409 CONFLICT (duplicate email, license, etc.) — create this
  - `BusinessRuleException` → 400 (driver not available, invalid status transition, etc.) — create this
  - **Why not `IllegalArgumentException`?** It’s a JDK exception for programming errors. If any third-party library throws it internally, your `GlobalExceptionHandler` would catch it and return a 400 with leaked internal messages. Domain exceptions are explicit and safe.
- Services work with DTOs (accept Request, return Response), NOT entities — conversion happens inside the service using mappers
- **Logging guidelines:**
  - `log.info()` for write operations (create, update, delete)
  - `log.debug()` for read operations
  - NEVER log sensitive data (passwords, tokens, PII)

### Controllers
- `@RestController` + `@RequestMapping("/api/{entity-plural}")`
- `@RequiredArgsConstructor` for injection
- Inject the combined `{Entity}Service` interface (not Read/Write separately)
- Use `@Valid @RequestBody` for POST/PUT request validation
- HTTP methods: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`
- Return `ResponseEntity<T>` with appropriate status codes:
  - `200 OK` — successful GET/PUT/PATCH
  - `201 CREATED` — successful POST
  - `204 NO_CONTENT` — successful DELETE
  - `400 BAD_REQUEST` — validation/business errors
  - `404 NOT_FOUND` — resource not found
  - `409 CONFLICT` — duplicate resource
- **Controllers MUST NOT contain try-catch blocks.** All exceptions propagate to `GlobalExceptionHandler` automatically. try-catch in controllers = duplicated error handling + swallowed error messages.
- List endpoints MUST support pagination using Spring’s `Pageable` (see Pagination section below)

### Exception Handling
- `GlobalExceptionHandler` (`@RestControllerAdvice`) handles all exceptions centrally:
  - `ResourceNotFoundException` → 404
  - `DuplicateResourceException` → 409
  - `BusinessRuleException` → 400
  - `MethodArgumentNotValidException` → 400 with field-level error details
  - Generic `Exception` → 500
- **Use a typed `ErrorResponse` class, NOT `Map<String, Object>`:**
  ```java
  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  public class ErrorResponse {
      private int status;
      private String message;
      private LocalDateTime timestamp;
      private Map<String, String> fieldErrors; // only for validation errors
  }
  ```
  Why: compile-time safety, consistent response shape, proper Swagger/OpenAPI doc generation, and clients can deserialize into a typed object.
- Logging in exception handler: `log.error()` for 500s, `log.warn()` for 400/404/409

### Configuration
- `RedisConfig` — configures `RedisTemplate<String, Object>` with String keys + JSON value serialization
- `application.properties` — uses `${ENV_VAR}` placeholders loaded from `.env` file via spring-dotenv
- Database secrets (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) and Redis config (`REDIS_HOST`, `REDIS_PORT`) come from `.env` file (NOT committed to git)

### Pagination
- **All list endpoints MUST support pagination** — returning unbounded `findAll()` results will crash clients and servers at scale
- Use Spring’s `Pageable` parameter in service and repository methods
- `findAll(Pageable pageable)` returns `Page<Response>`
- Controller accepts `?page=0&size=20&sort=createdAt,desc`
- Default page size: 20, max: 100
- Repositories: use `PagingAndSortingRepository` methods or `JpaRepository.findAll(Pageable)`

---

## Current Domain Model

### Entities & Relationships
```
Passenger (1) ──── (N) Booking (N) ──── (1) Driver
```
- A **Passenger** can have many bookings
- A **Driver** can have many bookings
- A **Booking** belongs to one passenger and optionally one driver
- Driver availability (`isAvailable`) is managed during booking lifecycle

### Booking Status Flow
```
PENDING → CONFIRMED → IN_PROGRESS → COMPLETED
   ↓          ↓           ↓
 CANCELLED  CANCELLED   CANCELLED
```
- PENDING: Created without driver, or driver not yet confirmed
- CONFIRMED: Driver assigned and accepted
- IN_PROGRESS: Ride started (`actualPickupTime` set)
- COMPLETED: Ride finished (`completedAt` set, driver released)
- CANCELLED: Booking cancelled at any stage (driver released)

### Driver Location (Redis)
- Real-time driver GPS stored in Redis (not PostgreSQL)
- Key pattern: `driver:location:{driverId}`
- Used for nearby driver search, online status tracking

---

## API Endpoints

Refer to controller classes for current endpoint definitions. Each controller's `@RequestMapping` and method-level annotations are the source of truth.

API path convention: `/api/{entity-plural}` (e.g., `/api/drivers`, `/api/passengers`, `/api/bookings`). Use `/api/v1/` prefix for newer or versioned endpoints.

---

## How to Add a New Domain Entity (Step-by-Step Template)

When adding a new entity (e.g., `Payment`, `Rating`, `Vehicle`), follow this exact order:

1. **Entity** → `entity/{Entity}.java`
   - `@Entity`, `@Table`, `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
   - `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with `@EqualsAndHashCode.Include` on `id`
   - Audit timestamps with `@PrePersist/@PreUpdate`
   - **NO `@Data` on entities**

2. **Repository** → `repository/{Entity}Repository.java`
   - `extends JpaRepository<Entity, Long>`, add derived query methods

3. **Request DTO** → `dto/{Entity}Request.java`
   - Validation annotations on all required fields

4. **Response DTO** → `dto/{Entity}Response.java`
   - Plain data class, matches what API consumers need

5. **Mapper** → `mapper/{Entity}Mapper.java`
   - `@Component`, three methods: `toEntity()`, `toResponse()`, `updateEntity()`

6. **Read Service Interface** → `service/{Entity}ReadService.java`
   - `findById`, `findAll`, domain-specific queries

7. **Write Service Interface** → `service/{Entity}WriteService.java`
   - `create`, `update`, `deleteById`

8. **Combined Service Interface** → `service/{Entity}Service.java`
   - `extends {Entity}ReadService, {Entity}WriteService`

9. **Service Implementation** → `service/impl/{Entity}ServiceImpl.java`
   - `@Service`, `@RequiredArgsConstructor`, `@Slf4j`, `@Transactional(readOnly = true)` at class level, `@Transactional` on write methods
   - Implement all methods

10. **Controller** → `controller/{Entity}Controller.java`
    - `@RestController`, `@RequestMapping("/api/{entities}")`, full CRUD endpoints

---

## Dependencies

See `build.gradle` for the current dependency list. Do not duplicate it here.

---

## Environment Setup

See `README.md` for setup instructions (PostgreSQL, Redis, `.env` file configuration).

---

## Rules for AI Agents Working on This Project

1. **DO NOT** modify code directly unless explicitly asked. Show code and explain what/why/how.
2. **ALWAYS** follow the layered architecture — never skip layers.
3. **ALWAYS** use the DTO pattern — never return entities from controllers.
4. **ALWAYS** use the 3-interface service pattern (Read + Write + Combined).
5. **ALWAYS** use manual mappers with `@Component` — no MapStruct.
6. **ALWAYS** use constructor injection via `@RequiredArgsConstructor` — no `@Autowired`.
7. **ALWAYS** use `@Builder` pattern for object creation.
8. **ALWAYS** use `@Transactional(readOnly = true)` as class-level default on service impls; override with `@Transactional` on write methods.
9. **ALWAYS** put validation annotations on Request DTOs, not on entities.
10. **ALWAYS** handle audit timestamps via `@PrePersist` / `@PreUpdate` in entities.
11. **ALWAYS** use domain-specific exceptions (`ResourceNotFoundException`, `DuplicateResourceException`, `BusinessRuleException`) — never `IllegalArgumentException` for business logic.
12. **ALWAYS** use typed `ErrorResponse` class for error responses — never `Map<String, Object>`.
13. **ALWAYS** support pagination on list endpoints using `Pageable`.
14. **ALWAYS** use `@Slf4j` for logging in services and exception handlers.
15. **NEVER** use `@Autowired` or field injection.
16. **NEVER** use `@Data` on JPA entities — use `@Getter` + `@Setter` + explicit `@EqualsAndHashCode`.
17. **NEVER** expose entity classes in API responses.
18. **NEVER** put business logic in controllers — controllers are thin, NO try-catch blocks.
19. **NEVER** define enums as inner classes inside entities — use separate files.
20. **NEVER** use `@Query` unless derived query methods are insufficient.
21. **FOLLOW** the naming conventions table exactly when creating new files.
22. **FOLLOW** the "How to Add a New Domain Entity" template for any new feature.