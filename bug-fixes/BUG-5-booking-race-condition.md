# Bug Fix 5 — Race Condition in Booking Creation (Driver Double-Booking)

**Branch:** `fix/bug-5-booking-race-condition`
**Files fixed:**
- `src/main/java/com/jitendra/RideOrbit/repository/DriverRepository.java`
- `src/main/java/com/jitendra/RideOrbit/service/impl/BookingServiceImpl.java`

---

## What Was the Bug?

`BookingServiceImpl.create()` had a classic **check-then-act** race condition:

```java
// OLD — race condition
driver = driverRepository.findById(request.getDriverId())  // 1. READ driver
    .orElseThrow(...);

if (!driver.getIsAvailable()) {          // 2. CHECK availability
    throw new IllegalArgumentException(...);
}
// ← GAP: another transaction can slip in here

driver.setIsAvailable(false);            // 3. SET unavailable
driverRepository.save(driver);          // 4. WRITE driver
```

Between step 2 (reading availability = `true`) and step 4 (writing `isAvailable = false`), there is a window where another concurrent transaction can execute the same steps 1–2, also see `isAvailable = true`, and proceed to assign the same driver.

---

## The Race Condition Visualized

```
Thread A (Booking for Passenger 1)   Thread B (Booking for Passenger 2)
────────────────────────────────     ────────────────────────────────
findById(driverId=5)
  → isAvailable = true
                                     findById(driverId=5)
                                       → isAvailable = true

if (!driver.getIsAvailable()) →
  skips, driver IS available         if (!driver.getIsAvailable()) →
                                       skips, driver IS available ← BUG: same driver!

driver.setIsAvailable(false)         driver.setIsAvailable(false)
driverRepository.save(driver)        driverRepository.save(driver)
bookingRepository.save(booking A)    bookingRepository.save(booking B)

Result: Driver 5 is assigned to BOTH Booking A and Booking B.
        Both have isAvailable = false but the driver has two active bookings.
```

---

## Why This Happens

`@Transactional` wraps the whole `create()` method in a database transaction, but a transaction alone does **not** prevent concurrent reads in the default `READ_COMMITTED` isolation level. Two transactions can both read the same row before either has written their change.

The fix requires acquiring an **exclusive lock** on the driver row at read time, so the second thread must wait until the first thread's transaction completes before it can read the driver's availability.

---

## The Fix: Pessimistic Write Lock

### 1. New locked query method in `DriverRepository`

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT d FROM Driver d WHERE d.id = :id")
Optional<Driver> findByIdForUpdate(Long id);
```

`LockModeType.PESSIMISTIC_WRITE` translates to `SELECT ... FOR UPDATE` in SQL. PostgreSQL holds an exclusive row-level lock on this driver row for the duration of the current transaction. Any other transaction that tries to call `findByIdForUpdate()` for the same driver will **block and wait** until the lock is released.

### 2. Use the locked method in `BookingServiceImpl.create()` and `update()`

```java
// NEW — race condition eliminated
driver = driverRepository.findByIdForUpdate(request.getDriverId())  // acquires row lock
    .orElseThrow(...);

if (!driver.getIsAvailable()) {    // check while holding the lock
    throw new IllegalArgumentException(...);
}

driver.setIsAvailable(false);      // modify while holding the lock
driverRepository.save(driver);    // write
// lock released when @Transactional commits
```

---

## Fixed Race Condition Visualization

```
Thread A (Booking for Passenger 1)   Thread B (Booking for Passenger 2)
────────────────────────────────     ────────────────────────────────
findByIdForUpdate(driverId=5)
  → acquires row lock on Driver 5
  → isAvailable = true

                                     findByIdForUpdate(driverId=5)
                                       → BLOCKS — row is locked by Thread A

if (!driver.getIsAvailable()) → ok
driver.setIsAvailable(false)
driverRepository.save(driver)
bookingRepository.save(booking A)
COMMIT → releases lock
                                       → lock released, Thread B unblocks
                                     findByIdForUpdate reads: isAvailable = false
                                     if (!driver.getIsAvailable()) →
                                       throws "Driver is not available" ← correct!

Result: Only Booking A is created. Booking B is rejected with a clear error.
```

---

## Why Pessimistic Locking Over Optimistic Locking

**Optimistic locking** (`@Version`) would also prevent the double-booking — the second transaction would get an `ObjectOptimisticLockingFailureException` at commit time. However:

1. The exception type (`ObjectOptimisticLockingFailureException`) is less intuitive to handle and translate into a meaningful HTTP response.
2. In a booking system, contention on popular drivers is expected. Optimistic locking causes failed transactions that must be retried, increasing load under exactly the conditions where you need less load.
3. Pessimistic locking gives the correct, clean error: "Driver is not available" rather than a generic "conflict" error.

For low-write scenarios (e.g., driver profile updates), optimistic locking is preferred. For high-contention availability checks, pessimistic locking is the right choice.

---

## Transaction Scope

The lock is held for the duration of the `@Transactional` method (`create()` or `update()`). The flow is:

```
BEGIN TRANSACTION
  ├── findByIdForUpdate(driverId) → acquires SELECT FOR UPDATE lock
  ├── check availability
  ├── driver.setIsAvailable(false)
  ├── driverRepository.save(driver)
  └── bookingRepository.save(booking)
COMMIT → releases all locks
```

The lock duration is milliseconds (a single in-process booking creation), so the performance impact of briefly blocking other threads is negligible.

---

## Files Changed

```
src/main/java/com/jitendra/RideOrbit/repository/DriverRepository.java
src/main/java/com/jitendra/RideOrbit/service/impl/BookingServiceImpl.java
```
