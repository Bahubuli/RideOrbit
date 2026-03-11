package com.jitendra.RideOrbit.service;

import com.jitendra.RideOrbit.dto.PassengerRequest;
import com.jitendra.RideOrbit.dto.PassengerResponse;

/**
 * Interface for Passenger write operations
 * Following Interface Segregation Principle
 */
public interface PassengerWriteService {
    PassengerResponse create(PassengerRequest request);
    PassengerResponse update(Long id, PassengerRequest request);
    void deleteById(Long id);
}

