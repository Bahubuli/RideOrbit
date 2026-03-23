package com.jitendra.RideOrbit.controller;

import com.jitendra.RideOrbit.dto.DriverLocationRequest;
import com.jitendra.RideOrbit.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * Handles inbound WebSocket messages from drivers.
 *
 * Drivers connect once over WebSocket and send location updates on the same
 * connection every 3-5 seconds. This is far more efficient than the driver
 * making a new HTTP POST every few seconds — no TCP handshake, no HTTP overhead,
 * no need to manage polling timers that fight with the driver app lifecycle.
 *
 * Client sends to: /app/driver/location
 * (the /app prefix is configured in WebSocketConfig as the application destination prefix)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LocationMessageController {

    private final LocationService locationService;

    /**
     * Receive a driver's GPS coordinates over the existing WebSocket connection
     * and persist them to Redis.
     *
     * The driver's mobile app sends this every 3-5 seconds while online.
     * No HTTP call, no new connection — reuses the same STOMP session.
     *
     * @param request - DriverLocationRequest containing driverId, latitude, longitude
     */
    @MessageMapping("/driver/location")
    public void updateDriverLocation(@Valid DriverLocationRequest request) {
        locationService.saveDriverLocation(request);
        log.debug("Location updated via WebSocket — driver {} at ({}, {})",
                request.getDriverId(), request.getLatitude(), request.getLongitude());
    }
}
