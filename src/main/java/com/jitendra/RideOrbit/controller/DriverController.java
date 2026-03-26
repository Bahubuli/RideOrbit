package com.jitendra.RideOrbit.controller;

import com.jitendra.RideOrbit.dto.DriverLocationRequest;
import com.jitendra.RideOrbit.dto.DriverLocationResponse;
import com.jitendra.RideOrbit.dto.DriverRequest;
import com.jitendra.RideOrbit.dto.DriverResponse;
import com.jitendra.RideOrbit.service.DriverService;
import com.jitendra.RideOrbit.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final LocationService locationService;
    
    @GetMapping
    public ResponseEntity<List<DriverResponse>> getAllDrivers() {
        List<DriverResponse> drivers = driverService.findAll();
        return ResponseEntity.ok(drivers);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<DriverResponse> getDriverById(@PathVariable Long id) {
        return driverService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/email/{email}")
    public ResponseEntity<DriverResponse> getDriverByEmail(@PathVariable String email) {
        return driverService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/available")
    public ResponseEntity<List<DriverResponse>> getAvailableDrivers() {
        List<DriverResponse> drivers = driverService.findAvailableDrivers();
        return ResponseEntity.ok(drivers);
    }
    
    @PostMapping
    public ResponseEntity<DriverResponse> createDriver(@Valid @RequestBody DriverRequest request) {
        DriverResponse driver = driverService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(driver);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DriverResponse> updateDriver(
            @PathVariable Long id,
            @Valid @RequestBody DriverRequest request) {
        DriverResponse driver = driverService.update(id, request);
        return ResponseEntity.ok(driver);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDriver(@PathVariable Long id) {
        driverService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/online")
    public ResponseEntity<DriverLocationResponse> goOnline(
            @PathVariable Long id,
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        DriverLocationRequest request = new DriverLocationRequest(id, latitude, longitude);
        DriverLocationResponse response = locationService.saveDriverLocation(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/offline")
    public ResponseEntity<Void> goOffline(@PathVariable Long id) {
        locationService.removeDriverLocation(id);
        return ResponseEntity.noContent().build();
    }
}

