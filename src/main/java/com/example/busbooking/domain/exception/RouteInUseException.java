package com.example.busbooking.domain.exception;

public class RouteInUseException extends BusinessException {
    public RouteInUseException() {
        super("Route still has scheduled or in-progress trips");
    }
}
