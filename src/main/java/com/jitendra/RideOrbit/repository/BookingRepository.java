package com.jitendra.RideOrbit.repository;

import com.jitendra.RideOrbit.entity.Booking;
import com.jitendra.RideOrbit.entity.Driver;
import com.jitendra.RideOrbit.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByPassenger(Passenger passenger);
    List<Booking> findByDriver(Driver driver);
    Optional<Booking> findByDriver_IdAndStatusIn(Long driverId, List<Booking.BookingStatus> statuses);
}

