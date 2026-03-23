package com.jitendra.RideOrbit.controller;

import com.jitendra.RideOrbit.dto.BookingResponse;
import com.jitendra.RideOrbit.entity.Booking;
import com.jitendra.RideOrbit.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long id) {
        return bookingService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/passenger/{passengerId}")
    public ResponseEntity<List<BookingResponse>> getBookingsByPassenger(@PathVariable Long passengerId) {
        List<BookingResponse> bookings = bookingService.findByPassengerId(passengerId);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<List<BookingResponse>> getBookingsByDriver(@PathVariable Long driverId) {
        List<BookingResponse> bookings = bookingService.findByDriverId(driverId);
        return ResponseEntity.ok(bookings);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BookingResponse> updateBookingStatus(
            @PathVariable Long id,
            @RequestParam Booking.BookingStatus status) {
        BookingResponse booking = bookingService.updateStatus(id, status);
        return ResponseEntity.ok(booking);
    }
}
