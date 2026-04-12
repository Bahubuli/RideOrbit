package com.jitendra.RideOrbit.service;

import com.jitendra.RideOrbit.dto.DriverLocationRequest;
import com.jitendra.RideOrbit.dto.DriverLocationResponse;
import java.util.List;

/**
 * Service interface for managing driver locations using Redis geospatial data
 * Handles real-time driver location tracking and proximity searches
 */
public interface LocationService {
    
    /**
     * Save or update driver location in Redis using geospatial data
     * @param request - DriverLocationRequest containing driverId, latitude, longitude
     * @return DriverLocationResponse with saved data
     */
    DriverLocationResponse saveDriverLocation(DriverLocationRequest request);
    
    /**
     * Get specific driver's current location
     * @param driverId - Driver ID
     * @return DriverLocationResponse with coordinates and timestamp
     */
    DriverLocationResponse getDriverLocation(Long driverId);
    
    /**
     * Find all drivers within given radius from a specific point
     * @param latitude - Reference point latitude (e.g., passenger location)
     * @param longitude - Reference point longitude (e.g., passenger location)
     * @param radiusInKm - Search radius in kilometers
     * @return List of nearby drivers sorted by distance
     */
    List<DriverLocationResponse> getNearbyDrivers(Double latitude, Double longitude, Double radiusInKm);
    
    /**
     * Remove driver location from Redis (when driver goes offline)
     * @param driverId - Driver ID
     * @return boolean - true if removed successfully, false if not found
     */
    boolean removeDriverLocation(Long driverId);
    
    /**
     * Get all active driver locations
     * @return List of all drivers currently online
     */
    List<DriverLocationResponse> getAllActiveDriverLocations();
    
    /**
     * Check if driver location exists in Redis
     * @param driverId - Driver ID
     * @return true if driver is online, false otherwise
     */
    boolean isDriverOnline(Long driverId);

}
