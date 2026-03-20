# Bug Fix 3 — Controllers Swallowing Exception Details

**Branch:** `fix/bug-3-controller-exception-handling`
**Files fixed:**
- `src/main/java/com/jitendra/RideOrbit/controller/DriverController.java`
- `src/main/java/com/jitendra/RideOrbit/controller/PassengerController.java`
- `src/main/java/com/jitendra/RideOrbit/controller/BookingController.java`

---

## What Was the Bug?

Every mutating endpoint (`POST`, `PUT`, `DELETE`) in all three controllers wrapped the service call in a try-catch block:

```java
// OLD — buggy pattern (same in all 3 controllers)
@PostMapping
public ResponseEntity<DriverResponse> createDriver(@Valid @RequestBody DriverRequest request) {
    try {
        DriverResponse driver = driverService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(driver);
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().build(); // ← empty body, no message
    }
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteDriver(@PathVariable Long id) {
    try {
        driverService.deleteById(id);
        return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
        return ResponseEntity.notFound().build(); // ← empty body, no message
    }
}
```

### Why This Was Wrong

The project already has a `GlobalExceptionHandler` annotated with `@RestControllerAdvice` that correctly handles `IllegalArgumentException`:

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
    Map<String, String> error = new HashMap<>();
    error.put("message", e.getMessage());   // ← includes the actual error message
    error.put("status", HttpStatus.BAD_REQUEST.toString());
    return ResponseEntity.badRequest().body(error);
}
```

The controller-level try-catch blocks **intercepted the exception before it could reach the global handler**. The result:

| Scenario | Old Response | Correct Response |
|---|---|---|
| Create driver with duplicate email | `400 Bad Request` — **empty body** | `400 Bad Request` — `{"message": "Driver with email ... already exists"}` |
| Delete passenger that doesn't exist | `404 Not Found` — **empty body** | `400 Bad Request` — `{"message": "Passenger not found with id: 5"}` |
| Update booking with invalid driver | `400 Bad Request` — **empty body** | `400 Bad Request` — `{"message": "Driver not found with id: 99"}` |

Clients (mobile apps, frontend) received no information about what went wrong. They could not display a meaningful error to the user.

### Inconsistency in Delete Endpoints

The delete endpoints caught `IllegalArgumentException` and returned `404 Not Found` with an empty body. But the service throws `IllegalArgumentException` for not-found, not `ResourceNotFoundException`. Mapping the wrong exception to `404` was also incorrect semantics — the `GlobalExceptionHandler` maps `ResourceNotFoundException` to 404 and `IllegalArgumentException` to 400.

---

## The Fix

Remove all try-catch blocks from the controllers. Spring's `@RestControllerAdvice` mechanism propagates uncaught exceptions to the global handler automatically.

```java
// NEW — correct pattern
@PostMapping
public ResponseEntity<DriverResponse> createDriver(@Valid @RequestBody DriverRequest request) {
    DriverResponse driver = driverService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(driver);
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteDriver(@PathVariable Long id) {
    driverService.deleteById(id);
    return ResponseEntity.noContent().build();
}
```

When `driverService.create()` throws `IllegalArgumentException("Driver with email ... already exists")`, the exception now propagates up to `GlobalExceptionHandler.handleIllegalArgumentException()`, which returns:

```json
HTTP 400 Bad Request
{
  "message": "Driver with email john@example.com already exists",
  "status": "400 BAD_REQUEST"
}
```

---

## How @RestControllerAdvice Works

```
HTTP Request
    │
    ▼
DispatcherServlet
    │
    ▼
Controller method  ─── throws IllegalArgumentException ──►  GlobalExceptionHandler
                                                                 @ExceptionHandler(IllegalArgumentException.class)
                                                                 returns 400 with JSON body
```

`@RestControllerAdvice` is a cross-cutting concern. It catches exceptions from **any** controller in the application. Controllers should let exceptions propagate naturally — they should not handle infrastructure-level concerns like HTTP error formatting.

---

## Endpoints Fixed

### DriverController
- `POST /api/drivers` — create driver
- `PUT /api/drivers/{id}` — update driver
- `DELETE /api/drivers/{id}` — delete driver

### PassengerController
- `POST /api/passengers` — create passenger
- `PUT /api/passengers/{id}` — update passenger
- `DELETE /api/passengers/{id}` — delete passenger

### BookingController
- `GET /api/bookings/passenger/{id}` — get bookings by passenger
- `GET /api/bookings/driver/{id}` — get bookings by driver
- `POST /api/bookings` — create booking
- `PUT /api/bookings/{id}` — update booking
- `PATCH /api/bookings/{id}/status` — update booking status
- `DELETE /api/bookings/{id}` — delete booking

---

## Files Changed

```
src/main/java/com/jitendra/RideOrbit/controller/DriverController.java
src/main/java/com/jitendra/RideOrbit/controller/PassengerController.java
src/main/java/com/jitendra/RideOrbit/controller/BookingController.java
```
