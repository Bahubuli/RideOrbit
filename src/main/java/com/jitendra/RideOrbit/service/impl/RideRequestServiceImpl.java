package com.jitendra.RideOrbit.service.impl;

import com.jitendra.RideOrbit.dto.BookingResponse;
import com.jitendra.RideOrbit.dto.DriverLocationResponse;
import com.jitendra.RideOrbit.dto.PendingRideRequest;
import com.jitendra.RideOrbit.dto.RideRequestDTO;
import com.jitendra.RideOrbit.dto.RideRequestNotification;
import com.jitendra.RideOrbit.dto.RideRequestResponse;
import com.jitendra.RideOrbit.entity.Booking;
import com.jitendra.RideOrbit.entity.Driver;
import com.jitendra.RideOrbit.entity.Passenger;
import com.jitendra.RideOrbit.mapper.BookingMapper;
import com.jitendra.RideOrbit.repository.BookingRepository;
import com.jitendra.RideOrbit.repository.DriverRepository;
import com.jitendra.RideOrbit.repository.PassengerRepository;
import com.jitendra.RideOrbit.service.BookingNotificationService;
import com.jitendra.RideOrbit.service.LocationService;
import com.jitendra.RideOrbit.service.RideRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideRequestServiceImpl implements RideRequestService {

    private static final double SEARCH_RADIUS_KM = 5.0;
    private static final double BASE_FARE = 50.0;
    private static final double RATE_PER_KM = 15.0;
    private static final String RIDE_REQUEST_KEY_PREFIX = "ride-request:pending:";
    private static final long RIDE_REQUEST_TTL_MINUTES = 5;

    private final LocationService locationService;
    private final PassengerRepository passengerRepository;
    private final DriverRepository driverRepository;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final BookingNotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public RideRequestResponse requestRide(RideRequestDTO request) {

        Passenger passenger = passengerRepository.findById(request.getPassengerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Passenger not found: " + request.getPassengerId()));

        List<DriverLocationResponse> nearbyDrivers = locationService.getNearbyDrivers(
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                SEARCH_RADIUS_KM
        );

        String rideRequestId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal estimatedFare = calculateFare(
                request.getPickupLatitude(), request.getPickupLongitude(),
                request.getDropoffLatitude(), request.getDropoffLongitude()
        );

        // Notify each nearby driver via WebSocket — including drivers currently on a booking.
        // getNearbyDrivers() only filters out offline drivers (expired data hash).
        List<Long> notifiedDriverIds = new ArrayList<>();
        for (DriverLocationResponse nearbyDriver : nearbyDrivers) {

            RideRequestNotification notification = RideRequestNotification.builder()
                    .rideRequestId(rideRequestId)
                    .passengerId(passenger.getId())
                    .passengerName(passenger.getName())
                    .pickupLatitude(request.getPickupLatitude())
                    .pickupLongitude(request.getPickupLongitude())
                    .pickupAddress(request.getPickupAddress())
                    .dropoffLatitude(request.getDropoffLatitude())
                    .dropoffLongitude(request.getDropoffLongitude())
                    .dropoffAddress(request.getDropoffAddress())
                    .distanceToPickup(nearbyDriver.getDistanceFromPoint())
                    .estimatedFare(estimatedFare)
                    .timestamp(now)
                    .build();

            messagingTemplate.convertAndSend(
                    "/topic/driver." + nearbyDriver.getDriverId() + ".ride-request",
                    notification
            );

            notifiedDriverIds.add(nearbyDriver.getDriverId());

            log.info("Ride request {} sent to driver {} ({} km away)",
                    rideRequestId, nearbyDriver.getDriverId(), nearbyDriver.getDistanceFromPoint());
        }

        // Store the pending request in Redis so any instance can handle the accept call.
        // TTL of 5 minutes — if no driver accepts, the request expires automatically.
        if (!notifiedDriverIds.isEmpty()) {
            PendingRideRequest pending = PendingRideRequest.builder()
                    .rideRequestId(rideRequestId)
                    .passengerId(passenger.getId())
                    .pickupLatitude(request.getPickupLatitude())
                    .pickupLongitude(request.getPickupLongitude())
                    .pickupAddress(request.getPickupAddress())
                    .dropoffLatitude(request.getDropoffLatitude())
                    .dropoffLongitude(request.getDropoffLongitude())
                    .dropoffAddress(request.getDropoffAddress())
                    .estimatedFare(estimatedFare)
                    .notifiedDriverIds(notifiedDriverIds)
                    .timestamp(now)
                    .build();

            String key = RIDE_REQUEST_KEY_PREFIX + rideRequestId;
            redisTemplate.opsForValue().set(key, pending, RIDE_REQUEST_TTL_MINUTES, TimeUnit.MINUTES);
        }

        log.info("Ride request {} — {} drivers notified near ({}, {})",
                rideRequestId, notifiedDriverIds.size(),
                request.getPickupLatitude(), request.getPickupLongitude());

        return RideRequestResponse.builder()
                .rideRequestId(rideRequestId)
                .passengerId(passenger.getId())
                .pickupAddress(request.getPickupAddress())
                .dropoffAddress(request.getDropoffAddress())
                .estimatedFare(estimatedFare)
                .driversNotified(notifiedDriverIds.size())
                .status(notifiedDriverIds.isEmpty() ? "NO_DRIVERS_FOUND" : "SEARCHING")
                .timestamp(now)
                .build();
    }

    @Override
    @Transactional
    public BookingResponse acceptRide(String rideRequestId, Long driverId) {

        // Atomic get-and-delete — only one driver can win per ride request.
        // GETDEL is a single Redis command: returns the value and deletes the key atomically.
        // If two drivers hit different app instances at the same time, only one gets the value.
        String key = RIDE_REQUEST_KEY_PREFIX + rideRequestId;
        PendingRideRequest pending = (PendingRideRequest) redisTemplate.opsForValue().getAndDelete(key);

        if (pending == null) {
            throw new IllegalArgumentException(
                    "Ride request not found or already accepted: " + rideRequestId);
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));

        Passenger passenger = passengerRepository.findById(pending.getPassengerId())
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found: " + pending.getPassengerId()));

        Booking booking = Booking.builder()
                .passenger(passenger)
                .driver(driver)
                .pickupLocation(pending.getPickupAddress())
                .dropoffLocation(pending.getDropoffAddress())
                .fare(pending.getEstimatedFare())
                .status(Booking.BookingStatus.CONFIRMED)
                .build();

        Booking saved = bookingRepository.save(booking);
        BookingResponse response = bookingMapper.toResponse(saved);

        // Notify passenger via /topic/booking.{id}
        notificationService.notifyBookingUpdate(response);

        // Dismiss other notified drivers
        for (Long notifiedDriverId : pending.getNotifiedDriverIds()) {
            if (!notifiedDriverId.equals(driverId)) {
                messagingTemplate.convertAndSend(
                        "/topic/driver." + notifiedDriverId + ".ride-request",
                        Map.of(
                                "rideRequestId", rideRequestId,
                                "status", "TAKEN",
                                "message", "This ride has been accepted by another driver"
                        )
                );
            }
        }

        log.info("Ride request {} accepted by driver {} — booking {} created",
                rideRequestId, driverId, saved.getId());

        return response;
    }

    /**
     * Haversine distance between pickup and dropoff, multiplied by rate per km.
     */
    private BigDecimal calculateFare(double pickupLat, double pickupLng,
                                     double dropoffLat, double dropoffLng) {
        double R = 6371;
        double dLat = Math.toRadians(dropoffLat - pickupLat);
        double dLon = Math.toRadians(dropoffLng - pickupLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(pickupLat)) * Math.cos(Math.toRadians(dropoffLat))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = R * c;

        return BigDecimal.valueOf(BASE_FARE + distanceKm * RATE_PER_KM)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
