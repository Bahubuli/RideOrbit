package com.jitendra.RideOrbit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pushed over WebSocket to /topic/driver.{driverId}.ride-request
 * when a passenger requests a ride and this driver is within 5km radius.
 * The driver sees pickup/dropoff details and their own distance to the pickup point.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestNotification {

    private String rideRequestId;       // UUID — unique ID for this ride request
    private Long passengerId;
    private String passengerName;

    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupAddress;

    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String dropoffAddress;

    private Double distanceToPickup;    // driver's current distance to pickup (km)
    private BigDecimal estimatedFare;    // fare estimate so driver sees what the ride pays

    private LocalDateTime timestamp;
}
