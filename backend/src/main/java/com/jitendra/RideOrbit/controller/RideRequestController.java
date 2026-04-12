package com.jitendra.RideOrbit.controller;

import com.jitendra.RideOrbit.dto.BookingResponse;
import com.jitendra.RideOrbit.dto.RideRequestDTO;
import com.jitendra.RideOrbit.dto.RideRequestResponse;
import com.jitendra.RideOrbit.service.RideRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ride-requests")
@RequiredArgsConstructor
public class RideRequestController {

    private final RideRequestService rideRequestService;

    @PostMapping
    public ResponseEntity<RideRequestResponse> requestRide(@Valid @RequestBody RideRequestDTO request) {
        RideRequestResponse response = rideRequestService.requestRide(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{rideRequestId}/accept")
    public ResponseEntity<BookingResponse> acceptRide(
            @PathVariable String rideRequestId,
            @RequestParam Long driverId) {
        BookingResponse response = rideRequestService.acceptRide(rideRequestId, driverId);
        return ResponseEntity.ok(response);
    }
}
