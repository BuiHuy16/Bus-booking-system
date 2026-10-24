package com.example.busbooking.domain.model;

public record Vehicle(Long id, int seatCapacity, com.example.busbooking.domain.enums.VehicleStatus status) {}
