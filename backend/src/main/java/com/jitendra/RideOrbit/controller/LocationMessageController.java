package com.jitendra.RideOrbit.controller;

import com.jitendra.RideOrbit.dto.DriverLocationRequest;
import com.jitendra.RideOrbit.dto.DriverLocationResponse;
import com.jitendra.RideOrbit.service.BookingService;
import com.jitendra.RideOrbit.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
 *
 * If the driver has an active booking (CONFIRMED or IN_PROGRESS), the updated
 * location is forwarded to the passenger on:
 *   /topic/passenger.{passengerId}.driver-location
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LocationMessageController {

    private final LocationService locationService;
    private final BookingService bookingService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Receive a driver's GPS coordinates over the existing WebSocket connection,
     * persist them to Redis, and — if the driver is on an active ride — push the
     * updated location to the passenger in real time.
     *
     * The driver's mobile app sends this every 3-5 seconds while online.
     * No HTTP call, no new connection — reuses the same STOMP session.
     */
    @MessageMapping("/driver/location")
    public void updateDriverLocation(@Valid DriverLocationRequest request) {
        DriverLocationResponse location = locationService.saveDriverLocation(request);

        // If driver has an active booking, push location to the passenger
        bookingService.findActiveByDriverId(request.getDriverId())
                .ifPresent(booking -> {
                    messagingTemplate.convertAndSend(
                            "/topic/passenger." + booking.getPassengerId() + ".driver-location",
                            location
                    );
                    log.debug("Location pushed to passenger {} — driver {} at ({}, {})",
                            booking.getPassengerId(), request.getDriverId(),
                            request.getLatitude(), request.getLongitude());
                });
    }
}
