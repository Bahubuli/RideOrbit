package com.jitendra.RideOrbit.service;

import com.jitendra.RideOrbit.dto.BookingRequest;
import com.jitendra.RideOrbit.dto.BookingResponse;
import com.jitendra.RideOrbit.entity.Booking;

/**
 * Interface for Booking write operations
 * Following Interface Segregation Principle
 */
public interface BookingWriteService {
    BookingResponse create(BookingRequest request);
    BookingResponse update(Long id, BookingRequest request);
    BookingResponse updateStatus(Long id, Booking.BookingStatus status);
    void deleteById(Long id);
}

