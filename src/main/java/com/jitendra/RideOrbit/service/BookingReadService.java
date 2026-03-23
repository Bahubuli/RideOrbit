package com.jitendra.RideOrbit.service;

import com.jitendra.RideOrbit.dto.BookingResponse;

import java.util.List;
import java.util.Optional;

/**
 * Interface for Booking read operations
 * Following Interface Segregation Principle
 */
public interface BookingReadService {
    Optional<BookingResponse> findById(Long id);
    List<BookingResponse> findByPassengerId(Long passengerId);
    List<BookingResponse> findByDriverId(Long driverId);
}

