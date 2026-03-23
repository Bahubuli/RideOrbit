package com.jitendra.RideOrbit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Stored in Redis when a ride request is broadcast to nearby drivers.
 * Holds everything needed to create a booking when a driver accepts.
 * TTL of 5 minutes — if no driver accepts within that window, the request expires.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRideRequest {
    private String rideRequestId;
    private Long passengerId;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupAddress;
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String dropoffAddress;
    private BigDecimal estimatedFare;
    private List<Long> notifiedDriverIds;
    private LocalDateTime timestamp;
}
