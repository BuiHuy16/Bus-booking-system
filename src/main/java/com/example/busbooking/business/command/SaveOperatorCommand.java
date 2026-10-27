package com.example.busbooking.business.command;

import com.example.busbooking.domain.enums.OperatorStatus;

public record SaveOperatorCommand(String name, String address, String phone, String email,
                                  OperatorStatus status) {
}
