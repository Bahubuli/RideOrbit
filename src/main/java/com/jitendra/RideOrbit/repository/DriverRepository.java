package com.jitendra.RideOrbit.repository;

import com.jitendra.RideOrbit.entity.Driver;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByEmail(String email);
    Optional<Driver> findByLicenseNumber(String licenseNumber);
    boolean existsByEmail(String email);
    boolean existsByLicenseNumber(String licenseNumber);

    /**
     * Fetch a driver with a pessimistic write lock (SELECT ... FOR UPDATE).
     * Use this when you need to atomically check-then-modify the driver row
     * (e.g., checking availability and marking as unavailable during booking).
     * The lock is held for the duration of the transaction, preventing any
     * other transaction from reading or modifying this row until the current
     * transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Driver d WHERE d.id = :id")
    Optional<Driver> findByIdForUpdate(Long id);
}

