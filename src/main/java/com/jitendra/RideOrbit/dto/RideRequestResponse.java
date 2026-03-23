package com.jitendra.RideOrbit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Returned to the passenger after a ride request is submitted.
 * Tells them how many drivers were notified and the current status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestResponse {

    private String rideRequestId;       // same UUID sent to drivers
    private Long passengerId;
    private String pickupAddress;
    private String dropoffAddress;
    private int driversNotified;        // how many drivers received the notification
    private String status;              // SEARCHING or NO_DRIVERS_FOUND
    private LocalDateTime timestamp;
}
