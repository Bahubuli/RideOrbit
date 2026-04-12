package com.jitendra.RideOrbit.repository;

import com.jitendra.RideOrbit.entity.Booking;
import com.jitendra.RideOrbit.entity.Driver;
import com.jitendra.RideOrbit.entity.Passenger;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Eagerly fetch both passenger and driver in a single JOIN — prevents N+1 on list endpoints
    @EntityGraph(attributePaths = {"passenger", "driver"})
    List<Booking> findByPassenger(Passenger passenger);

    @EntityGraph(attributePaths = {"passenger", "driver"})
    List<Booking> findByDriver(Driver driver);

    @EntityGraph(attributePaths = {"passenger", "driver"})
    Optional<Booking> findByDriver_IdAndStatusIn(Long driverId, List<Booking.BookingStatus> statuses);

    @EntityGraph(attributePaths = {"passenger", "driver"})
    Optional<Booking> findById(Long id);
}

