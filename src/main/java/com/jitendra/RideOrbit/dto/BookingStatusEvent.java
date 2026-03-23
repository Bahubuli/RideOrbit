package com.jitendra.RideOrbit.dto;

import com.jitendra.RideOrbit.entity.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Pushed over WebSocket to /topic/booking.{bookingId} whenever
 * a booking is created or its status changes.
 * Both the passenger and the driver subscribe to this topic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingStatusEvent {
    private Long bookingId;
    private Booking.BookingStatus status;
    private Long passengerId;
    private Long driverId;
    private String message;
    private LocalDateTime timestamp;
}
