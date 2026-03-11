package com.jitendra.RideOrbit.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DriverLocationResponse {
    private Long driverId;
    private Double latitude;
    private Double longitude;
    private LocalDateTime lastUpdated;
    private Double distanceFromPoint;
}
