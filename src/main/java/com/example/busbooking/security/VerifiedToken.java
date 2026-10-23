package com.example.busbooking.security;

import java.time.Instant;

public record VerifiedToken(Long userId, Instant issuedAt, Instant expiresAt) { }
