package com.jitendra.RideOrbit.service.impl;

import com.jitendra.RideOrbit.dto.BookingResponse;
import com.jitendra.RideOrbit.entity.Booking;
import com.jitendra.RideOrbit.entity.Driver;
import com.jitendra.RideOrbit.entity.Passenger;
import com.jitendra.RideOrbit.mapper.BookingMapper;
import com.jitendra.RideOrbit.repository.BookingRepository;
import com.jitendra.RideOrbit.repository.DriverRepository;
import com.jitendra.RideOrbit.repository.PassengerRepository;
import com.jitendra.RideOrbit.service.BookingNotificationService;
import com.jitendra.RideOrbit.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final PassengerRepository passengerRepository;
    private final DriverRepository driverRepository;
    private final BookingMapper bookingMapper;
    private final BookingNotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingResponse> findById(Long id) {
        return bookingRepository.findById(id)
                .map(bookingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> findByPassengerId(Long passengerId) {
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found with id: " + passengerId));
        return bookingRepository.findByPassenger(passenger).stream()
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> findByDriverId(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found with id: " + driverId));
        return bookingRepository.findByDriver(driver).stream()
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BookingResponse updateStatus(Long id, Booking.BookingStatus status) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + id));

        booking.setStatus(status);

        if (status == Booking.BookingStatus.IN_PROGRESS && booking.getActualPickupTime() == null) {
            booking.setActualPickupTime(LocalDateTime.now());
        } else if (status == Booking.BookingStatus.COMPLETED) {
            booking.setCompletedAt(LocalDateTime.now());
        }

        Booking updatedBooking = bookingRepository.save(booking);
        BookingResponse response = bookingMapper.toResponse(updatedBooking);
        notificationService.notifyBookingUpdate(response);
        return response;
    }
}
