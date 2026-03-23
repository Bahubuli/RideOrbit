# RideOrbit

A ride-hailing backend API built with Spring Boot 3, PostgreSQL, Redis, and RabbitMQ. Implements the Uber-style flow — passenger requests a ride, nearby drivers are notified in real-time via WebSocket, first driver to accept gets the booking.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5.7 (Java 21) |
| Database | PostgreSQL 17 |
| Cache / Geo | Redis 7 (geospatial index, pending ride request storage) |
| Message Broker | RabbitMQ 3 (STOMP over WebSocket) |
| Real-time | WebSocket (STOMP protocol) |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation |
| Containerization | Docker + Docker Compose |
| Testing | JUnit 5, Testcontainers, Spring Boot Test |

## How It Works

```
Passenger requests a ride
    ↓
System calculates fare (Haversine distance)
    ↓
Redis GEORADIUS finds nearby drivers within 5 km
    ↓
Each nearby driver gets a WebSocket notification (includes fare, pickup/dropoff)
    ↓
Ride request stored in Redis with 5-min TTL
    ↓
First driver to tap "Accept" wins (Redis GETDEL — atomic, race-safe)
    ↓
Booking created, passenger notified, other drivers dismissed
    ↓
Ride lifecycle: CONFIRMED → IN_PROGRESS → COMPLETED / CANCELLED
```

## Quick Start with Docker

**Prerequisites:** Docker Desktop running.

```bash
git clone <repo-url>
cd RideOrbit
docker compose up --build
```

The API is available at `http://localhost:8080`.
RabbitMQ management UI is at `http://localhost:15672` (user: `rideorbit_user`, password: `rideorbit_password`).

To stop:
```bash
docker compose down
```

To stop and wipe database volumes:
```bash
docker compose down -v
```

---

## Local Development (without Docker)

**Prerequisites:** Java 21, PostgreSQL 17, Redis 7, RabbitMQ 3 (with STOMP plugin enabled).

1. Create a PostgreSQL database:
   ```sql
   CREATE DATABASE rideorbit_db;
   CREATE USER rideorbit_user WITH PASSWORD 'rideorbit_password';
   GRANT ALL PRIVILEGES ON DATABASE rideorbit_db TO rideorbit_user;
   ```

2. Ensure Redis is running on `localhost:6379`.

3. Ensure RabbitMQ is running with the STOMP plugin enabled on port `61613`.

4. Create a `.env` file in the project root:
   ```env
   DB_URL=jdbc:postgresql://localhost:5432/rideorbit_db
   DB_USERNAME=rideorbit_user
   DB_PASSWORD=rideorbit_password
   REDIS_HOST=localhost
   REDIS_PORT=6379
   RABBITMQ_HOST=localhost
   RABBITMQ_USERNAME=rideorbit_user
   RABBITMQ_PASSWORD=rideorbit_password
   RABBITMQ_STOMP_PORT=61613
   ```

5. Run the application:
   ```bash
   ./gradlew bootRun
   ```

---

## Running Tests

Tests use Testcontainers — Docker must be running. No external database or Redis instance needed.

```bash
./gradlew test
```

---

## API Reference

### Ride Requests — `/api/ride-requests`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/ride-requests` | Request a ride — finds nearby drivers and notifies them |
| `POST` | `/api/ride-requests/{id}/accept?driverId=N` | Driver accepts — booking created, others dismissed |

**Request ride body:**
```json
{
  "passengerId": 1,
  "pickupLatitude": 28.6139,
  "pickupLongitude": 77.2090,
  "pickupAddress": "Connaught Place, Delhi",
  "dropoffLatitude": 28.5355,
  "dropoffLongitude": 77.3910,
  "dropoffAddress": "Noida Sector 18"
}
```

**Response:**
```json
{
  "rideRequestId": "f3a2c1d0-...",
  "passengerId": 1,
  "pickupAddress": "Connaught Place, Delhi",
  "dropoffAddress": "Noida Sector 18",
  "estimatedFare": 380.00,
  "driversNotified": 3,
  "status": "SEARCHING",
  "timestamp": "2026-03-22T10:30:00"
}
```

---

### Bookings — `/api/bookings`

Bookings are created internally when a driver accepts a ride request. These endpoints manage the booking lifecycle.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/bookings/{id}` | Get booking by ID |
| `GET` | `/api/bookings/passenger/{passengerId}` | Get bookings for a passenger (ride history) |
| `GET` | `/api/bookings/driver/{driverId}` | Get bookings for a driver (ride history) |
| `PATCH` | `/api/bookings/{id}/status?status={STATUS}` | Update booking status |

**Status lifecycle:** `CONFIRMED` → `IN_PROGRESS` → `COMPLETED` / `CANCELLED`

---

### Drivers — `/api/drivers`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/drivers` | List all drivers |
| `GET` | `/api/drivers/{id}` | Get driver by ID |
| `GET` | `/api/drivers/email/{email}` | Get driver by email |
| `POST` | `/api/drivers` | Create a driver |
| `PUT` | `/api/drivers/{id}` | Update a driver |
| `DELETE` | `/api/drivers/{id}` | Delete a driver |

---

### Passengers — `/api/passengers`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/passengers` | List all passengers |
| `GET` | `/api/passengers/{id}` | Get passenger by ID |
| `GET` | `/api/passengers/email/{email}` | Get passenger by email |
| `POST` | `/api/passengers` | Create a passenger |
| `PUT` | `/api/passengers/{id}` | Update a passenger |
| `DELETE` | `/api/passengers/{id}` | Delete a passenger |

---

### Driver Location — `/api/v1/drivers/location`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/drivers/location/{driverId}` | Get driver's current location |
| `GET` | `/api/v1/drivers/location/active` | List all online drivers |
| `GET` | `/api/v1/drivers/location/nearby?latitude=&longitude=&radiusInKm=` | Find nearby drivers |
| `GET` | `/api/v1/drivers/location/{driverId}/online-status` | Check if driver is online |
| `DELETE` | `/api/v1/drivers/location/{driverId}` | Remove driver's location (go offline) |

Driver location updates are sent over **WebSocket** (not HTTP):
```
STOMP destination: /app/driver/location
Body: { "driverId": 1, "latitude": 28.6139, "longitude": 77.2090 }
```

Location data is stored in Redis with a 6-hour TTL. Drivers whose TTL has expired are considered offline.

---

### WebSocket Topics

Clients connect to `ws://localhost:8080/ws` using STOMP protocol.

| Topic | Subscriber | Events |
|---|---|---|
| `/topic/driver.{driverId}.ride-request` | Driver | New ride request notifications, dismissals |
| `/topic/passenger.{passengerId}.ride-updates` | Passenger | Booking status updates (CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED) |
| `/topic/driver.{driverId}.ride-updates` | Driver | Booking status updates for their rides |

---

## Environment Variables

| Variable | Default (Docker) | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://postgres:5432/rideorbit_db` | JDBC connection URL |
| `DB_USERNAME` | `rideorbit_user` | Database username |
| `DB_PASSWORD` | `rideorbit_password` | Database password |
| `REDIS_HOST` | `redis` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `RABBITMQ_HOST` | `rabbitmq` | RabbitMQ hostname |
| `RABBITMQ_USERNAME` | `rideorbit_user` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `rideorbit_password` | RabbitMQ password |
| `RABBITMQ_STOMP_PORT` | `61613` | RabbitMQ STOMP port |

---

## Project Structure

```
src/
├── main/java/com/jitendra/RideOrbit/
│   ├── controller/
│   │   ├── DriverController.java          # Driver CRUD
│   │   ├── PassengerController.java       # Passenger CRUD
│   │   ├── RideRequestController.java     # Ride request + acceptance
│   │   ├── BookingController.java         # Booking queries + status updates
│   │   └── LocationMessageController.java # WebSocket: driver location updates
│   ├── service/
│   │   ├── impl/
│   │   │   ├── RideRequestServiceImpl.java  # Core: ride request, acceptance, fare
│   │   │   ├── BookingServiceImpl.java      # Booking queries + status updates
│   │   │   └── LocationServiceImpl.java     # Redis geo operations
│   │   ├── BookingNotificationService.java  # WebSocket notifications for bookings
│   │   └── ...                              # Service interfaces
│   ├── entity/              # JPA entities (Driver, Passenger, Booking)
│   ├── dto/                 # Request / Response DTOs
│   ├── mapper/              # Entity ↔ DTO mappers
│   ├── repository/          # Spring Data JPA repositories
│   ├── exception/           # GlobalExceptionHandler, ResourceNotFoundException
│   └── config/              # Redis + WebSocket configuration
├── main/resources/
│   └── application.properties
└── test/

docs/
├── features/    # Feature documentation (detailed step-by-step)
├── bugs/        # Bug fix notes
├── plans/       # Project plans and setup guides
└── study-notes/ # Development notes
```

---

## Documentation

Detailed feature documentation is in `docs/features/`:

1. [Redis Implementation](docs/features/1.%20redis-implementation.md) — geospatial index, driver state hash, Redis config
2. [RabbitMQ & WebSocket Migration](docs/features/2.1%20rabbitmq-websocket-migration.md) — STOMP over RabbitMQ
3. [Ride Request](docs/features/4.%20ride-request.md) — nearby driver search, fare calculation, WebSocket notifications
4. [Ride Acceptance & Booking Cleanup](docs/features/5.%20ride-acceptance-and-booking-cleanup.md) — acceptance flow, Redis GETDEL, booking lifecycle, status updates
