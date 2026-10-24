package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataRouteRepository extends JpaRepository<RouteEntity, Long> {}
