package com.example.busbooking.domain.model;

import com.example.busbooking.domain.enums.OperatorStatus;
import java.time.LocalDateTime;

public record Operator(Long id, String name, String address, String phone, String email,
                       OperatorStatus status, LocalDateTime createdAt) {
}
