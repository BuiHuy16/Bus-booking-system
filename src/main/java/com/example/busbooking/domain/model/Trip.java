package com.example.busbooking.domain.model;

import com.example.busbooking.domain.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Trip {
    private Long id;
    private Long routeId;
    private Long vehicleId;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private BigDecimal price;
    private int availableSeats;
    private TripStatus status;
    private final RouteSummary route;
    private final VehicleSummary vehicle;

    public Trip(
            Long id,
            Long routeId,
            Long vehicleId,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            BigDecimal price,
            int availableSeats,
            TripStatus status
    ) {
        this(id, routeId, vehicleId, departureTime, arrivalTime, price, availableSeats, status, null, null);
    }

    public Trip(
            Long id,
            Long routeId,
            Long vehicleId,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            BigDecimal price,
            int availableSeats,
            TripStatus status,
            RouteSummary route,
            VehicleSummary vehicle
    ) {
        this.id = id;
        this.routeId = routeId;
        this.vehicleId = vehicleId;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.price = price;
        this.availableSeats = availableSeats;
        this.status = status;
        this.route = route;
        this.vehicle = vehicle;
    }

    public Long getId() { return id; }
    public Long getRouteId() { return routeId; }
    public Long getVehicleId() { return vehicleId; }
    public LocalDateTime getDepartureTime() { return departureTime; }
    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public BigDecimal getPrice() { return price; }
    public int getAvailableSeats() { return availableSeats; }
    public TripStatus getStatus() { return status; }
    public RouteSummary getRoute() { return route; }
    public VehicleSummary getVehicle() { return vehicle; }
}
