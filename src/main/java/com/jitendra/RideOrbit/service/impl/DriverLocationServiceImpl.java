package com.jitendra.RideOrbit.service.impl;

import com.jitendra.RideOrbit.dto.DriverLocationRequest;
import com.jitendra.RideOrbit.dto.DriverLocationResponse;
import com.jitendra.RideOrbit.service.DriverLocationService;
import com.jitendra.RideOrbit.service.LocationService;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Implementation of DriverLocationService using LocationService
 * Delegates all geospatial operations to LocationService
 */
@Service
public class DriverLocationServiceImpl implements DriverLocationService {
    
    private final LocationService locationService;
    
    public DriverLocationServiceImpl(LocationService locationService) {
        this.locationService = locationService;
    }
    
    @Override
    public DriverLocationResponse saveDriverLocation(DriverLocationRequest request) {
        return locationService.saveDriverLocation(request);
    }
    
    @Override
    public DriverLocationResponse getDriverLocation(Long driverId) {
        return locationService.getDriverLocation(driverId);
    }
    
    @Override
    public List<DriverLocationResponse> getNearbyDrivers(Double latitude, Double longitude, Double radiusInKm) {
        return locationService.getNearbyDrivers(latitude, longitude, radiusInKm);
    }
    
    @Override
    public boolean removeDriverLocation(Long driverId) {
        return locationService.removeDriverLocation(driverId);
    }
    
    @Override
    public List<DriverLocationResponse> getAllActiveDriverLocations() {
        return locationService.getAllActiveDriverLocations();
    }
    
    @Override
    public boolean isDriverOnline(Long driverId) {
        return locationService.isDriverOnline(driverId);
    }
}

