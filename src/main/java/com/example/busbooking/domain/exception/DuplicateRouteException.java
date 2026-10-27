package com.example.busbooking.domain.exception;

public class DuplicateRouteException extends BusinessException {
    public DuplicateRouteException() {
        super("Route already exists");
    }
}
