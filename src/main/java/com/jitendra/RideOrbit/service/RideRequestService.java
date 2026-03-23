package com.jitendra.RideOrbit.service;

import com.jitendra.RideOrbit.dto.BookingResponse;
import com.jitendra.RideOrbit.dto.RideRequestDTO;
import com.jitendra.RideOrbit.dto.RideRequestResponse;

public interface RideRequestService {

    /**
     * Find all nearby drivers and push a ride request notification
     * to each of them over WebSocket. The request is stored in memory
     * so a driver can accept it later.
     */
    RideRequestResponse requestRide(RideRequestDTO request);

    /**
     * Called when a driver accepts a ride request.
     * Creates a booking, notifies the passenger, and dismisses other notified drivers.
     */
    BookingResponse acceptRide(String rideRequestId, Long driverId);
}
