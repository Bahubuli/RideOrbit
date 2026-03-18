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

/**
 * Implementation of LocationService using Redis geospatial operations
 * Uses Redis sorted sets with geospatial indices for efficient location querying
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
     * Represents a circular search area for geospatial queries
     */
    private static class Circle {
        private final Point center;
        private final Distance radius;
        
        public Circle(Double latitude, Double longitude, Double radiusInKm) {
            this.center = new Point(longitude, latitude);
            this.radius = new Distance(radiusInKm, Metrics.KILOMETERS);
        }
        
        public Point getCenter() {
            return center;
        }
        
        public Distance getRadius() {
            return radius;
        }
    }
    
    /**
     * Save driver location in Redis using geospatial index
     * Stores location data in both geospatial sorted set and hash for quick retrieval
     */
    @Override
    public DriverLocationResponse saveDriverLocation(DriverLocationRequest request) {
        try {
            // Add to geospatial index
            Point point = new Point(request.getLongitude(), request.getLatitude());
            redisTemplate.opsForGeo().add(DRIVER_GEO_KEY, point, String.valueOf(request.getDriverId()));
            
            // Store location data in hash for quick retrieval
            String dataKey = DRIVER_DATA_KEY + request.getDriverId();
            redisTemplate.opsForHash().put(dataKey, "driverId", request.getDriverId());
            redisTemplate.opsForHash().put(dataKey, "latitude", request.getLatitude());
            redisTemplate.opsForHash().put(dataKey, "longitude", request.getLongitude());
            redisTemplate.opsForHash().put(dataKey, "lastUpdated", LocalDateTime.now().toString());
            
            // Set expiration to 6 hours for inactive drivers
            redisTemplate.expire(dataKey, java.time.Duration.ofHours(6));
            redisTemplate.expire(DRIVER_GEO_KEY, java.time.Duration.ofHours(6));
            
            return DriverLocationResponse.builder()
                    .driverId(request.getDriverId())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .lastUpdated(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to save driver location: " + e.getMessage(), e);
        }
    }
    
    /**
     * Find all drivers within the specified radius from a point
     * Uses Redis GEORADIUS command for efficient geospatial search
     */
    @Override
    public List<DriverLocationResponse> getNearbyDrivers(Double latitude, Double longitude, Double radiusInKm) {
        List<DriverLocationResponse> nearbyDrivers = new ArrayList<>();
        
        try {
            Point searchPoint = new Point(longitude, latitude);
            Distance radius = new Distance(radiusInKm, Metrics.KILOMETERS);
            
            // Query geospatial index to find drivers within radius
            GeoResults<RedisGeoCommands.GeoLocation<Object>> results = 
                redisTemplate.opsForGeo().radius(DRIVER_GEO_KEY, searchPoint, radius, 
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending());
            
            if (results != null) {
                for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : results.getContent()) {
                    String driverId = result.getContent().getName().toString();
                    double distance = result.getDistance().getValue();
                    
                    // Retrieve driver location data
                    String dataKey = DRIVER_DATA_KEY + driverId;
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(dataKey))) {
                        Long id = Long.valueOf(driverId);
                        Double driverLat = Double.valueOf(
                            redisTemplate.opsForHash().get(dataKey, "latitude").toString());
                        Double driverLon = Double.valueOf(
                            redisTemplate.opsForHash().get(dataKey, "longitude").toString());
                        
                        DriverLocationResponse response = DriverLocationResponse.builder()
                                .driverId(id)
                                .latitude(driverLat)
                                .longitude(driverLon)
                                .distanceFromPoint(distance)
                                .lastUpdated(LocalDateTime.now())
                                .build();
                        
                        nearbyDrivers.add(response);
                    }
                }
            }
            
            return nearbyDrivers;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get nearby drivers: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get specific driver's current location from Redis
     * Retrieves location data stored in hash
     */
    @Override
    public DriverLocationResponse getDriverLocation(Long driverId) {
        try {
            String dataKey = DRIVER_DATA_KEY + driverId;
            
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(dataKey))) {
                return null;
            }
            
            Long id = Long.valueOf(redisTemplate.opsForHash().get(dataKey, "driverId").toString());
            Double latitude = Double.valueOf(
                redisTemplate.opsForHash().get(dataKey, "latitude").toString());
            Double longitude = Double.valueOf(
                redisTemplate.opsForHash().get(dataKey, "longitude").toString());
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
     * Remove driver location from Redis (driver goes offline)
     * Removes from both geospatial index and data hash
     */
    @Override
    public boolean removeDriverLocation(Long driverId) {
        try {
            String dataKey = DRIVER_DATA_KEY + driverId;
            
            // Remove from geospatial index
            Long geoRemoved = redisTemplate.opsForGeo().remove(DRIVER_GEO_KEY, String.valueOf(driverId));
            
            // Remove data hash
            Long hashRemoved = redisTemplate.opsForHash().delete(dataKey, "driverId", "latitude", "longitude", "lastUpdated");
            
            // Delete the entire key if empty
            redisTemplate.delete(dataKey);
            
            return geoRemoved != null && geoRemoved > 0;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove driver location: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get all active driver locations from Redis
     * Retrieves all drivers currently in the geospatial index
     */
    @Override
    public List<DriverLocationResponse> getAllActiveDriverLocations() {
        List<DriverLocationResponse> allDrivers = new ArrayList<>();
        
        try {
            // Get all members from geospatial key with their positions
            GeoResults<RedisGeoCommands.GeoLocation<Object>> results = 
                redisTemplate.opsForGeo().radius(DRIVER_GEO_KEY, 
                    new Point(0, 0), new Distance(40075, Metrics.KILOMETERS), 
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeCoordinates()
                        .sortAscending());
            
            if (results != null) {
                for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : results.getContent()) {
                    String driverId = result.getContent().getName().toString();
                    Point location = result.getContent().getPoint();
                    
                    String dataKey = DRIVER_DATA_KEY + driverId;
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(dataKey))) {
                        Long id = Long.valueOf(driverId);
                        String lastUpdatedStr = redisTemplate.opsForHash().get(dataKey, "lastUpdated").toString();
                        
                        DriverLocationResponse response = DriverLocationResponse.builder()
                                .driverId(id)
                                .latitude(location.getY())
                                .longitude(location.getX())
                                .lastUpdated(LocalDateTime.parse(lastUpdatedStr))
                                .build();
                        
                        allDrivers.add(response);
                    }
                }
            }
            
            return allDrivers;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get all active driver locations: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if driver is currently online (exists in Redis)
     */
    @Override
    public boolean isDriverOnline(Long driverId) {
        try {
            String dataKey = DRIVER_DATA_KEY + driverId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(dataKey)) && 
                   redisTemplate.opsForGeo().position(DRIVER_GEO_KEY, String.valueOf(driverId)) != null;
        } catch (Exception e) {
            return false;
        }
    }
}


/*

.includeDistance() is a method that tells Redis to return the distance from the center point to each result in the query. Without it, you only get the locations, not how far away they are.

.newGeoRadiusArgs() is a static factory method that creates a new instance of RedisGeoCommands.GeoRadiusCommandArgs. This class lets you specify options for the geospatial radius query, like sorting, including coordinates, or including distances.

RedisGeoCommands.GeoRadiusCommandArgs is a class from Spring Data Redis. It's long because it's fully qualified (package + class name). It configures how the radius query behaves.


*/