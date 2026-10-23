package com.example.busbooking.business.port.security;

import com.example.busbooking.business.result.IssuedToken;

public interface TokenProvider {
    IssuedToken issue(Long userId);
}
