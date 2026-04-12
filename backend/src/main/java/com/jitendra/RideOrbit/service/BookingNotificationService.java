package com.jitendra.RideOrbit.service;

import com.jitendra.RideOrbit.dto.BookingResponse;
import com.jitendra.RideOrbit.dto.BookingStatusEvent;
import com.jitendra.RideOrbit.entity.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BookingNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Pushes a booking status event to the passenger's and driver's personal topics.
     *
     * Passenger subscribes to /topic/passenger.{passengerId}.ride-updates once.
     * Driver subscribes to /topic/driver.{driverId}.ride-updates once.
     * Both receive every event for every booking they are part of — CONFIRMED,
     * IN_PROGRESS, COMPLETED, CANCELLED — on the same topic.
     */
    public void notifyBookingUpdate(BookingResponse booking) {
        BookingStatusEvent event = BookingStatusEvent.builder()
                .bookingId(booking.getId())
                .status(booking.getStatus())
                .passengerId(booking.getPassengerId())
                .driverId(booking.getDriverId())
                .message(humanMessage(booking.getStatus()))
                .timestamp(LocalDateTime.now())
                .build();

        // Notify passenger
        messagingTemplate.convertAndSend(
                "/topic/passenger." + booking.getPassengerId() + ".ride-updates",
                event
        );

        // Notify driver (if assigned)
        if (booking.getDriverId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/driver." + booking.getDriverId() + ".ride-updates",
                    event
            );
        }
    }

    private String humanMessage(Booking.BookingStatus status) {
        return switch (status) {
            case PENDING     -> "Booking created — waiting for a driver";
            case CONFIRMED   -> "Driver confirmed your booking";
            case IN_PROGRESS -> "Your ride has started";
            case COMPLETED   -> "Ride completed successfully";
            case CANCELLED   -> "Booking has been cancelled";
        };
    }
}
