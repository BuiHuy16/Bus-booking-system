package com.example.busbooking.api.dto.operator;

import com.example.busbooking.domain.enums.OperatorStatus;
import java.time.LocalDateTime;

public record OperatorResponse(Long id, String name, String address, String phone, String email,
                               OperatorStatus status, LocalDateTime createdAt) {
}
