package com.jitendra.RideOrbit.controller;

import com.jitendra.RideOrbit.dto.DriverLocationRequest;
import com.jitendra.RideOrbit.dto.DriverLocationResponse;
import com.jitendra.RideOrbit.exception.ResourceNotFoundException;
import com.jitendra.RideOrbit.service.DriverLocationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/drivers/location")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DriverLocationController {

    private final DriverLocationService driverLocationService;

    public DriverLocationController(DriverLocationService driverLocationService) {
        this.driverLocationService = driverLocationService;
    }

    /**
     * Update driver's current location
     * Called frequently by driver's mobile app (every few seconds)
     * 
     * @param request - DriverLocationRequest containing driverId, latitude, longitude
     * @return Updated driver location response
     */
    @PostMapping("/update")
    public ResponseEntity<?> updateDriverLocation(@Valid @RequestBody DriverLocationRequest request) {
        DriverLocationResponse response = driverLocationService.saveDriverLocation(request);
        return ResponseEntity.ok(Map.of(
            "message", "Location updated successfully",
            "data", response
        ));
    }

    /**
     * Get specific driver's current location
     * 
     * @param driverId - Driver ID
     * @return Driver's current location
     */
    @GetMapping("/{driverId}")
    public ResponseEntity<?> getDriverLocation(@PathVariable Long driverId) {
        DriverLocationResponse location = driverLocationService.getDriverLocation(driverId);
        if (location == null) {
            throw new ResourceNotFoundException("Driver location", driverId);
        }
        return ResponseEntity.ok(location);
    }

    /**
     * Find all drivers near a specific location
     * Used for matching passengers with nearby drivers
     * 
     * @param latitude - Passenger's location latitude
     * @param longitude - Passenger's location longitude
     * @param radiusInKm - Search radius (default 5 km)
     * @return List of nearby drivers sorted by distance
     */
    @GetMapping("/nearby")
    public ResponseEntity<?> getNearbyDrivers(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "5.0") Double radiusInKm) {
        List<DriverLocationResponse> nearbyDrivers =
            driverLocationService.getNearbyDrivers(latitude, longitude, radiusInKm);
        return ResponseEntity.ok(Map.of(
            "nearbyDrivers", nearbyDrivers,
            "count", nearbyDrivers.size(),
            "searchRadius", radiusInKm + " km"
        ));
    }

    /**
     * Get all active drivers currently online
     * Used for admin dashboard and monitoring
     * 
     * @return List of all active drivers
     */
    @GetMapping("/active")
    public ResponseEntity<?> getAllActiveDrivers() {
        List<DriverLocationResponse> activeDrivers = driverLocationService.getAllActiveDriverLocations();
        return ResponseEntity.ok(Map.of(
            "activeDrivers", activeDrivers,
            "totalCount", activeDrivers.size()
        ));
    }

    /**
     * Check if a specific driver is online
     * 
     * @param driverId - Driver ID
     * @return Online status of the driver
     */
    @GetMapping("/{driverId}/online-status")
    public ResponseEntity<?> checkDriverOnlineStatus(@PathVariable Long driverId) {
        boolean isOnline = driverLocationService.isDriverOnline(driverId);
        return ResponseEntity.ok(Map.of(
            "driverId", driverId,
            "isOnline", isOnline
        ));
    }

    /**
     * Remove driver location from Redis
     * Called when driver goes offline or ends their shift
     * 
     * @param driverId - Driver ID
     * @return Removal status message
     */
    @DeleteMapping("/{driverId}")
    public ResponseEntity<?> removeDriverLocation(@PathVariable Long driverId) {
        boolean removed = driverLocationService.removeDriverLocation(driverId);
        if (!removed) {
            throw new ResourceNotFoundException("Driver", driverId);
        }
        return ResponseEntity.ok(Map.of("message", "Driver location removed successfully"));
    }
}
