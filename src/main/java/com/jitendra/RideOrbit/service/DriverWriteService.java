package com.jitendra.RideOrbit.service;

import com.jitendra.RideOrbit.dto.DriverRequest;
import com.jitendra.RideOrbit.dto.DriverResponse;

/**
 * Interface for Driver write operations
 * Following Interface Segregation Principle
 */
public interface DriverWriteService {
    DriverResponse create(DriverRequest request);
    DriverResponse update(Long id, DriverRequest request);
    void deleteById(Long id);
}

