package com.jitendra.RideOrbit.service.impl;

import com.jitendra.RideOrbit.dto.DriverLocationRequest;
import com.jitendra.RideOrbit.dto.DriverLocationResponse;
import com.jitendra.RideOrbit.service.LocationService;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implementation of LocationService using Redis geospatial operations.
 * Uses Redis sorted sets with geospatial indices for efficient location querying.
 */
@Service
public class LocationServiceImpl implements LocationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String DRIVER_GEO_KEY = "driver:location:geo";
    private static final String DRIVER_DATA_KEY = "driver:location:data:";

    public LocationServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Save driver location in Redis using geospatial index.
     * Stores location data in both the geo sorted set and a hash for quick retrieval.
     * Only the per-driver hash key gets a TTL — the shared geo key does not,
     * so other drivers' entries are not affected.
     */
    @Override
    public DriverLocationResponse saveDriverLocation(DriverLocationRequest request) {
        try {
            LocalDateTime now = LocalDateTime.now();

            // Add to geospatial index (shared key — NO TTL set here)
            Point point = new Point(request.getLongitude(), request.getLatitude());
            redisTemplate.opsForGeo().add(DRIVER_GEO_KEY, point, String.valueOf(request.getDriverId()));

            // Store metadata in a per-driver hash key
            String dataKey = DRIVER_DATA_KEY + request.getDriverId();
            redisTemplate.opsForHash().put(dataKey, "driverId", request.getDriverId());
            redisTemplate.opsForHash().put(dataKey, "latitude", request.getLatitude());
            redisTemplate.opsForHash().put(dataKey, "longitude", request.getLongitude());
            redisTemplate.opsForHash().put(dataKey, "lastUpdated", now.toString());

            // Only the per-driver data key gets a TTL (6 h of inactivity = offline)
            redisTemplate.expire(dataKey, java.time.Duration.ofHours(6));

            return DriverLocationResponse.builder()
                    .driverId(request.getDriverId())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .lastUpdated(now)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to save driver location: " + e.getMessage(), e);
        }
    }

    /**
     * Find all drivers within the given radius using Redis GEORADIUS.
     *
     * Redis executes the spatial query server-side in O(N+log M) time where N is the
     * number of results and M is the total number of entries in the geo set. This
     * replaces the previous O(N) client-side Haversine loop that loaded every driver.
     *
     * The results include distance and coordinates directly from Redis, so we only
     * need one extra hash lookup per matching driver to retrieve lastUpdated.
     */
    @Override
    public List<DriverLocationResponse> getNearbyDrivers(Double latitude, Double longitude, Double radiusInKm) {
        try {
            Circle searchArea = new Circle(
                    new Point(longitude, latitude),          // Redis geo: Point(lon, lat)
                    new Distance(radiusInKm, Metrics.KILOMETERS)
            );

            GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo().radius(
                    DRIVER_GEO_KEY,
                    searchArea,
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                            .includeDistance()
                            .includeCoordinates()
                            .sortAscending()
            );

            if (results == null) {
                return List.of();
            }

            List<DriverLocationResponse> nearbyDrivers = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : results.getContent()) {
                String driverIdStr = result.getContent().getName().toString();
                String dataKey = DRIVER_DATA_KEY + driverIdStr;

                // Skip drivers whose data hash has expired (they went inactive)
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(dataKey))) {
                    continue;
                }

                Object lastUpdatedObj = redisTemplate.opsForHash().get(dataKey, "lastUpdated");
                LocalDateTime lastUpdated = lastUpdatedObj != null
                        ? LocalDateTime.parse(lastUpdatedObj.toString())
                        : LocalDateTime.now();

                // Redis Point: x = longitude, y = latitude
                Point geoPoint = result.getContent().getPoint();

                nearbyDrivers.add(DriverLocationResponse.builder()
                        .driverId(Long.valueOf(driverIdStr))
                        .latitude(geoPoint.getY())
                        .longitude(geoPoint.getX())
                        .distanceFromPoint(result.getDistance().getValue())
                        .lastUpdated(lastUpdated)
                        .build());
            }

            return nearbyDrivers;

        } catch (Exception e) {
            throw new RuntimeException("Failed to get nearby drivers: " + e.getMessage(), e);
        }
    }

    /**
     * Get a specific driver's current location from Redis.
     */
    @Override
    public DriverLocationResponse getDriverLocation(Long driverId) {
        try {
            String dataKey = DRIVER_DATA_KEY + driverId;

            if (!Boolean.TRUE.equals(redisTemplate.hasKey(dataKey))) {
                return null;
            }

            Long id = Long.valueOf(redisTemplate.opsForHash().get(dataKey, "driverId").toString());
            Double latitude = Double.valueOf(redisTemplate.opsForHash().get(dataKey, "latitude").toString());
            Double longitude = Double.valueOf(redisTemplate.opsForHash().get(dataKey, "longitude").toString());
            String lastUpdatedStr = redisTemplate.opsForHash().get(dataKey, "lastUpdated").toString();

            return DriverLocationResponse.builder()
                    .driverId(id)
                    .latitude(latitude)
                    .longitude(longitude)
                    .lastUpdated(LocalDateTime.parse(lastUpdatedStr))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to get driver location: " + e.getMessage(), e);
        }
    }

    /**
     * Remove driver location from Redis when the driver goes offline.
     * Removes from both the geo index and the data hash.
     */
    @Override
    public boolean removeDriverLocation(Long driverId) {
        try {
            String dataKey = DRIVER_DATA_KEY + driverId;

            Long geoRemoved = redisTemplate.opsForGeo().remove(DRIVER_GEO_KEY, String.valueOf(driverId));
            redisTemplate.delete(dataKey);

            return geoRemoved != null && geoRemoved > 0;

        } catch (Exception e) {
            throw new RuntimeException("Failed to remove driver location: " + e.getMessage(), e);
        }
    }

    /**
     * Get all active driver locations by reading every member of the geo sorted set.
     */
    @Override
    public List<DriverLocationResponse> getAllActiveDriverLocations() {
        try {
            Set<Object> allMembers = redisTemplate.opsForZSet().range(DRIVER_GEO_KEY, 0, -1);

            if (allMembers == null || allMembers.isEmpty()) {
                return List.of();
            }

            List<DriverLocationResponse> allDrivers = new ArrayList<>();
            for (Object memberObj : allMembers) {
                String driverId = memberObj.toString();
                String dataKey = DRIVER_DATA_KEY + driverId;

                if (!Boolean.TRUE.equals(redisTemplate.hasKey(dataKey))) {
                    continue;
                }

                try {
                    Object latObj = redisTemplate.opsForHash().get(dataKey, "latitude");
                    Object lonObj = redisTemplate.opsForHash().get(dataKey, "longitude");
                    Object lastUpdatedObj = redisTemplate.opsForHash().get(dataKey, "lastUpdated");

                    if (latObj == null || lonObj == null || lastUpdatedObj == null) continue;

                    allDrivers.add(DriverLocationResponse.builder()
                            .driverId(Long.valueOf(driverId))
                            .latitude(Double.valueOf(latObj.toString()))
                            .longitude(Double.valueOf(lonObj.toString()))
                            .lastUpdated(LocalDateTime.parse(lastUpdatedObj.toString()))
                            .build());
                } catch (Exception innerEx) {
                    // Skip malformed entries rather than failing the whole response
                }
            }

            return allDrivers;

        } catch (Exception e) {
            throw new RuntimeException("Failed to get all active driver locations: " + e.getMessage(), e);
        }
    }

    /**
     * Check whether a driver is currently online (present in both the geo index and their data hash).
     */
    @Override
    public boolean isDriverOnline(Long driverId) {
        try {
            String dataKey = DRIVER_DATA_KEY + driverId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(dataKey))
                    && redisTemplate.opsForGeo().position(DRIVER_GEO_KEY, String.valueOf(driverId)) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
