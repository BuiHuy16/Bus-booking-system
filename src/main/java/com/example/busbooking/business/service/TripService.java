package com.example.busbooking.business.service;

import com.example.busbooking.business.port.repository.TripRepository;
import com.example.busbooking.business.port.repository.RouteRepository;
import com.example.busbooking.business.port.repository.VehicleRepository;
import com.example.busbooking.business.port.repository.BookingRepository;
import com.example.busbooking.business.command.SaveTripCommand;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.model.Vehicle;
import com.example.busbooking.domain.exception.BusinessException;
import com.example.busbooking.domain.exception.TripConflictException;
import com.example.busbooking.domain.exception.ResourceNotFoundException;
import com.example.busbooking.domain.model.Trip;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;

    public TripService(TripRepository tripRepository, RouteRepository routeRepository,
                       VehicleRepository vehicleRepository, BookingRepository bookingRepository) {
        this.tripRepository = tripRepository;
        this.routeRepository = routeRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public List<Trip> listForAdmin(Long routeId, TripStatus status, LocalDate departureDate) {
        return tripRepository.findForAdmin(routeId, status, departureDate);
    }

    @Transactional
    public Trip create(SaveTripCommand command) {
        Vehicle vehicle = validate(command);
        return tripRepository.save(new Trip(null, command.routeId(), command.vehicleId(),
                command.departureTime(), command.arrivalTime(), command.price(),
                vehicle.seatCapacity(), TripStatus.SCHEDULED));
    }

    @Transactional
    public Trip update(Long tripId, SaveTripCommand command) {
        Trip current = lockedTrip(tripId);
        requireScheduled(current);
        Vehicle vehicle = validate(command);
        int availableSeats = current.getAvailableSeats();
        if (!current.getVehicleId().equals(command.vehicleId())) {
            // Booking creation/cancellation takes this same trip lock before changing seats/status.
            long confirmedSeats = bookingRepository.countConfirmedSeats(tripId);
            if (confirmedSeats > vehicle.seatCapacity()) {
                throw new TripConflictException("Vehicle capacity is smaller than confirmed booked seats");
            }
            availableSeats = vehicle.seatCapacity() - (int) confirmedSeats;
        }
        return tripRepository.save(new Trip(tripId, command.routeId(), command.vehicleId(),
                command.departureTime(), command.arrivalTime(), command.price(), availableSeats, current.getStatus()));
    }

    private Trip lockedTrip(Long tripId) {
        return tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));
    }

    @Transactional
    public Trip cancel(Long tripId) {
        Trip current = lockedTrip(tripId);
        if (current.getStatus() == TripStatus.CANCELLED) return current;
        requireScheduled(current);
        // Phase 1 policy: refunds and mass booking cancellation require business approval.
        if (bookingRepository.countConfirmedSeats(tripId) > 0) {
            throw new TripConflictException("Trip has confirmed bookings and cannot be cancelled");
        }
        return tripRepository.save(new Trip(tripId, current.getRouteId(), current.getVehicleId(),
                current.getDepartureTime(), current.getArrivalTime(), current.getPrice(),
                current.getAvailableSeats(), TripStatus.CANCELLED));
    }

    private void requireScheduled(Trip trip) {
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            throw new TripConflictException("Only scheduled trips can be changed or cancelled");
        }
    }

    private Vehicle validate(SaveTripCommand command) {
        if (command == null || command.routeId() == null || command.routeId() <= 0
                || command.vehicleId() == null || command.vehicleId() <= 0
                || command.departureTime() == null || command.arrivalTime() == null
                || !command.arrivalTime().isAfter(command.departureTime())
                || command.price() == null || command.price().signum() < 0
                || command.price().scale() > 2
                || command.price().compareTo(new java.math.BigDecimal("9999999999.99")) > 0) {
            throw new BusinessException("Invalid trip times, price or references");
        }
        var route = routeRepository.findById(command.routeId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + command.routeId()));
        Vehicle vehicle = vehicleRepository.findById(command.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + command.vehicleId()));
        if (route.status() != RouteStatus.ACTIVE || vehicle.status() != VehicleStatus.ACTIVE) {
            throw new BusinessException("Route and vehicle must be active");
        }
        return vehicle;
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
