package com.jitendra.RideOrbit.controller;

import com.jitendra.RideOrbit.dto.RideRequestDTO;
import com.jitendra.RideOrbit.dto.RideRequestResponse;
import com.jitendra.RideOrbit.service.RideRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
