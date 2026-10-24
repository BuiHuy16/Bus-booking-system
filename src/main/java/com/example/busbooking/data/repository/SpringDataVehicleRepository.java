package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.VehicleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataVehicleRepository extends JpaRepository<VehicleEntity, Long> {}
