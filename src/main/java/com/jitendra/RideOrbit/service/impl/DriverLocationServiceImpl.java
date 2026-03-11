package com.jitendra.RideOrbit.service.impl;

import com.jitendra.RideOrbit.dto.DriverLocationRequest;
import com.jitendra.RideOrbit.dto.DriverLocationResponse;
import com.jitendra.RideOrbit.service.DriverLocationService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Implementation of DriverLocationService using Redis for geospatial operations
 */
@Service
public class DriverLocationServiceImpl implements DriverLocationService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String DRIVER_LOCATION_KEY = "driver:location:";
    
    public DriverLocationServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public void saveDriverLocation(DriverLocationRequest request) {
        String key = DRIVER_LOCATION_KEY + request.getDriverId();
        redisTemplate.opsForValue().set(key, request);
    }
    
    @Override
    public DriverLocationResponse getDriverLocation(String driverId) {
        String key = DRIVER_LOCATION_KEY + driverId;
        Object location = redisTemplate.opsForValue().get(key);
        
        if (location instanceof DriverLocationRequest) {
            DriverLocationRequest locationRequest = (DriverLocationRequest) location;
            return DriverLocationResponse.builder()
                    .driverId(locationRequest.getDriverId())
                    .latitude(locationRequest.getLatitude())
                    .longitude(locationRequest.getLongitude())
                    .lastUpdated(java.time.LocalDateTime.now())
                    .build();
        }
        return null;
    }
}
