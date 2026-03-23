package com.jitendra.RideOrbit.service;

import com.jitendra.RideOrbit.dto.RideRequestDTO;
import com.jitendra.RideOrbit.dto.RideRequestResponse;

public interface RideRequestService {

    /**
     * Find all available drivers within 5km of the pickup point
     * and push a ride request notification to each of them over WebSocket.
     *
     * @param request - passenger's pickup/dropoff coordinates and addresses
     * @return response with number of drivers notified and request status
     */
    RideRequestResponse requestRide(RideRequestDTO request);
}
