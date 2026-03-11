
package com.jitendra.RideOrbit.service;

import com.jitendra.RideOrbit.dto.DriverLocationRequest;
import com.jitendra.RideOrbit.dto.DriverLocationResponse;

/**
 * Service interface for managing driver location operations
 */
public interface DriverLocationService {
    
    void saveDriverLocation(DriverLocationRequest request);
    
    DriverLocationResponse getDriverLocation(String driverId);
}