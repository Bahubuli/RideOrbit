# Bug Fix 2 — Shared Redis Geo Key TTL Reset on Every Location Update

**Branch:** `fix/bug-2-shared-geo-key-ttl`
**File fixed:** `src/main/java/com/jitendra/RideOrbit/service/impl/LocationServiceImpl.java`

---

## What Was the Bug?

In `saveDriverLocation`, two TTL (Time-To-Live) expiry calls were made every time a driver sent a location update:

```java
// OLD — buggy code
redisTemplate.expire(dataKey, java.time.Duration.ofHours(6));      // per-driver hash key ✓ correct
redisTemplate.expire(DRIVER_GEO_KEY, java.time.Duration.ofHours(6)); // shared geo key ✗ WRONG
```

`DRIVER_GEO_KEY` (`"driver:location:geo"`) is a **single Redis sorted set shared by all drivers**. It holds every active driver's geospatial position as a single key. Setting a TTL on it is catastrophic for two reasons:

---

## Why This Was Wrong

### Problem 1: One driver's update silently extends ALL drivers' TTLs

Every driver's mobile app sends location updates every few seconds. Each call to `saveDriverLocation` resets the 6-hour clock on the entire geo set. So if Driver A is actively updating, Driver B's geo entry never expires — even if Driver B went offline 7 hours ago. The TTL on the shared key effectively never fires because at least one driver is always sending updates.

```
Timeline:
  T+0h  Driver A updates → geo key TTL = 6h
  T+1h  Driver A updates → geo key TTL = 6h (reset)
  T+2h  Driver A updates → geo key TTL = 6h (reset)
  ...
  T+7h  Driver B has been offline for 7 hours, but geo entry still exists
```

### Problem 2: If all drivers go quiet, the entire geo index is wiped

If no driver sends an update for 6 hours (e.g., dead hours of the night), the entire geo index is deleted. When the first driver comes back online in the morning, the geo set is empty — all other driver entries that were correctly stored are gone.

```
Timeline:
  T+0h   All drivers stop updating (quiet night)
  T+6h   DRIVER_GEO_KEY expires → ALL driver positions deleted
  T+6h1m Driver A sends first morning update → restores only Driver A
          Drivers B, C, D are invisible until they also update
```

### Problem 3: LocalDateTime.now() called twice

A secondary issue in the same method: `LocalDateTime.now()` is called once to store `lastUpdated` in the hash (line 67) and again to build the response object (line 76). These two calls happen nanoseconds apart and return slightly different timestamps. The stored timestamp and the returned timestamp are inconsistent.

---

## The Fix

### Fix 1: Remove TTL on the shared geo key entirely

```java
// NEW — correct
redisTemplate.expire(dataKey, java.time.Duration.ofHours(6)); // only the per-driver key
// Removed: redisTemplate.expire(DRIVER_GEO_KEY, ...);
```

**The shared geo key should never have a TTL.** It is a permanent index that grows and shrinks as drivers come online and go offline. Stale entries are cleaned up explicitly when a driver calls `removeDriverLocation` (logs out), not by TTL expiry.

The per-driver hash key (`driver:location:data:{id}`) correctly keeps its 6-hour TTL. When a driver stops sending updates, their hash key expires after 6 hours. The `isDriverOnline` check uses both the hash key and the geo position, so a driver whose hash has expired is treated as offline even if their position remains in the geo set.

### Fix 2: Single `LocalDateTime.now()` call

```java
// NEW
LocalDateTime now = LocalDateTime.now();
// ... used for both the hash store and the response
redisTemplate.opsForHash().put(dataKey, "lastUpdated", now.toString());
return DriverLocationResponse.builder()
    .lastUpdated(now)  // same instant
    .build();
```

---

## Two-Key Design Explained

The implementation uses two Redis keys per driver plus one shared key:

```
driver:location:geo                  ← shared sorted set, geo index of ALL drivers
                                       No TTL. Cleaned up by removeDriverLocation().

driver:location:data:{driverId}      ← per-driver hash with metadata
                                       TTL = 6 hours of inactivity → marks driver offline
```

When determining if a driver is "online", `isDriverOnline()` checks **both**:
1. The per-driver hash key exists (TTL not expired)
2. The geo index has a position for this driver

This means a driver whose hash has expired (inactive for 6+ hours) is correctly reported as offline even though their position might still linger in the geo set until they explicitly call logout.

---

## Before vs After

```java
// BEFORE
redisTemplate.expire(dataKey, Duration.ofHours(6));       // ✓ correct
redisTemplate.expire(DRIVER_GEO_KEY, Duration.ofHours(6)); // ✗ nukes all drivers

return DriverLocationResponse.builder()
    .lastUpdated(LocalDateTime.now()) // ✗ different instant than what was stored
    .build();

// AFTER
redisTemplate.expire(dataKey, Duration.ofHours(6));        // ✓ only per-driver key
// DRIVER_GEO_KEY has no TTL                               // ✓ shared key is permanent

return DriverLocationResponse.builder()
    .lastUpdated(now)  // ✓ same instant stored in hash
    .build();
```

---

## Files Changed

```
src/main/java/com/jitendra/RideOrbit/service/impl/LocationServiceImpl.java
```
