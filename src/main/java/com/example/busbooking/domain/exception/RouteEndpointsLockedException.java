package com.example.busbooking.domain.exception;

public class RouteEndpointsLockedException extends BusinessException {
    public RouteEndpointsLockedException() {
        super("Route endpoints cannot be changed after the route has trips");
    }
}
