package com.jitendra.RideOrbit.service.impl;

import com.jitendra.RideOrbit.dto.DriverLocationResponse;
import com.jitendra.RideOrbit.dto.RideRequestDTO;
import com.jitendra.RideOrbit.dto.RideRequestNotification;
import com.jitendra.RideOrbit.dto.RideRequestResponse;
import com.jitendra.RideOrbit.entity.Passenger;
import com.jitendra.RideOrbit.repository.PassengerRepository;
import com.jitendra.RideOrbit.service.LocationService;
import com.jitendra.RideOrbit.service.RideRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideRequestServiceImpl implements RideRequestService {

    private static final double SEARCH_RADIUS_KM = 5.0;

    private final LocationService locationService;
    private final PassengerRepository passengerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public RideRequestResponse requestRide(RideRequestDTO request) {

        // 1. Validate passenger exists
        Passenger passenger = passengerRepository.findById(request.getPassengerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Passenger not found: " + request.getPassengerId()));

        // 2. Find all drivers within 5km of the pickup point using Redis GEO.
        //    Redis runs GEORADIUS server-side — no loading all drivers into memory.
        //    Results are sorted by distance ascending (closest first).
        List<DriverLocationResponse> nearbyDrivers = locationService.getNearbyDrivers(
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                SEARCH_RADIUS_KM
        );

        // 3. Generate a unique ID for this ride request.
        //    Same ID is sent to all drivers so they can reference it when accepting.
        String rideRequestId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        // 4. Notify each nearby driver via WebSocket — including drivers currently on a booking.
        //    Drivers on an active ride can still accept the next request.
        //    getNearbyDrivers() only filters out offline drivers (expired data hash).
        int notifiedCount = 0;
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
                    .timestamp(now)
                    .build();

            // Each driver has their own topic so only they receive it
            messagingTemplate.convertAndSend(
                    "/topic/driver." + nearbyDriver.getDriverId() + ".ride-request",
                    notification
            );

            log.info("Ride request {} sent to driver {} ({} km away)",
                    rideRequestId, nearbyDriver.getDriverId(), nearbyDriver.getDistanceFromPoint());

            notifiedCount++;
        }

        log.info("Ride request {} — {} drivers notified near ({}, {})",
                rideRequestId, notifiedCount,
                request.getPickupLatitude(), request.getPickupLongitude());

        return RideRequestResponse.builder()
                .rideRequestId(rideRequestId)
                .passengerId(passenger.getId())
                .pickupAddress(request.getPickupAddress())
                .dropoffAddress(request.getDropoffAddress())
                .driversNotified(notifiedCount)
                .status(notifiedCount > 0 ? "SEARCHING" : "NO_DRIVERS_FOUND")
                .timestamp(now)
                .build();
    }
}
