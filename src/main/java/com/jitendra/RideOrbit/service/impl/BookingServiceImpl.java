package com.jitendra.RideOrbit.service.impl;

import com.jitendra.RideOrbit.dto.BookingRequest;
import com.jitendra.RideOrbit.dto.BookingResponse;
import com.jitendra.RideOrbit.entity.Booking;
import com.jitendra.RideOrbit.entity.Driver;
import com.jitendra.RideOrbit.entity.Passenger;
import com.jitendra.RideOrbit.mapper.BookingMapper;
import com.jitendra.RideOrbit.repository.BookingRepository;
import com.jitendra.RideOrbit.repository.DriverRepository;
import com.jitendra.RideOrbit.repository.PassengerRepository;
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
    
    @Override
    @Transactional(readOnly = true)
    public Optional<BookingResponse> findById(Long id) {
        return bookingRepository.findById(id)
                .map(bookingMapper::toResponse);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> findAll() {
        return bookingRepository.findAll().stream()
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
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
    public BookingResponse create(BookingRequest request) {
        Passenger passenger = passengerRepository.findById(request.getPassengerId())
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found with id: " + request.getPassengerId()));

        Driver driver = null;
        if (request.getDriverId() != null) {
            // Use a pessimistic write lock so no other transaction can read or
            // modify this driver row between our availability check and the save.
            // Without this lock, two concurrent booking requests for the same driver
            // could both pass the availability check and double-book the driver.
            driver = driverRepository.findByIdForUpdate(request.getDriverId())
                    .orElseThrow(() -> new IllegalArgumentException("Driver not found with id: " + request.getDriverId()));

            if (!driver.getIsAvailable()) {
                throw new IllegalArgumentException("Driver with id " + request.getDriverId() + " is not available");
            }
        }

        Booking booking = bookingMapper.toEntity(request, passenger, driver);

        // If driver is assigned, mark as unavailable inside the same locked transaction
        if (driver != null) {
            driver.setIsAvailable(false);
            driverRepository.save(driver);
        }

        Booking savedBooking = bookingRepository.save(booking);
        return bookingMapper.toResponse(savedBooking);
    }
    
    @Override
    public BookingResponse update(Long id, BookingRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + id));

        Passenger passenger = passengerRepository.findById(request.getPassengerId())
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found with id: " + request.getPassengerId()));

        Driver driver = null;
        if (request.getDriverId() != null) {
            // Pessimistic lock — same reason as create(): availability check and
            // update must be atomic to prevent concurrent double-booking.
            driver = driverRepository.findByIdForUpdate(request.getDriverId())
                    .orElseThrow(() -> new IllegalArgumentException("Driver not found with id: " + request.getDriverId()));
        }

        // Handle driver availability when updating
        Driver previousDriver = booking.getDriver();
        if (previousDriver != null && !previousDriver.equals(driver)) {
            previousDriver.setIsAvailable(true);
            driverRepository.save(previousDriver);
        }

        if (driver != null && !driver.equals(previousDriver)) {
            if (!driver.getIsAvailable()) {
                throw new IllegalArgumentException("Driver with id " + request.getDriverId() + " is not available");
            }
            driver.setIsAvailable(false);
            driverRepository.save(driver);
        }
        
        bookingMapper.updateEntity(booking, request, passenger, driver);
        Booking updatedBooking = bookingRepository.save(booking);
        return bookingMapper.toResponse(updatedBooking);
    }
    
    @Override
    public BookingResponse updateStatus(Long id, Booking.BookingStatus status) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + id));
        
        booking.setStatus(status);
        
        // Handle status-specific logic
        if (status == Booking.BookingStatus.IN_PROGRESS && booking.getActualPickupTime() == null) {
            booking.setActualPickupTime(LocalDateTime.now());
        } else if (status == Booking.BookingStatus.COMPLETED) {
            booking.setCompletedAt(LocalDateTime.now());
            // Release driver
            if (booking.getDriver() != null) {
                Driver driver = booking.getDriver();
                driver.setIsAvailable(true);
                driverRepository.save(driver);
            }
        } else if (status == Booking.BookingStatus.CANCELLED) {
            // Release driver
            if (booking.getDriver() != null) {
                Driver driver = booking.getDriver();
                driver.setIsAvailable(true);
                driverRepository.save(driver);
            }
        }
        
        Booking updatedBooking = bookingRepository.save(booking);
        return bookingMapper.toResponse(updatedBooking);
    }
    
    @Override
    public void deleteById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + id));
        
        // Release driver if assigned
        if (booking.getDriver() != null) {
            Driver driver = booking.getDriver();
            driver.setIsAvailable(true);
            driverRepository.save(driver);
        }
        
        bookingRepository.deleteById(id);
    }
}

