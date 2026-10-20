package com.example.busbooking.business.service;

import com.example.busbooking.business.port.repository.TripRepository;
import com.example.busbooking.domain.exception.ResourceNotFoundException;
import com.example.busbooking.domain.model.Trip;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    public List<Trip> searchTrips(
            Long originId,
            Long destinationId,
            LocalDate departureDate) {

        return tripRepository.search(
                originId,
                destinationId,
                departureDate
        );
    }

    public Trip getTripById(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Trip not found with id: " + tripId
                        )
                );
    }
}
