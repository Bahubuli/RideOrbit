package com.jitendra.RideOrbit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DriverLocationRequest {
    
    private Long driverId;
    private Double latitude;
    private Double longitude;
}
