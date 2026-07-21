package com.flux.fluxproject.model;

import java.time.Instant;

public record UpdatePostRequest(
        String text,
        Instant scheduledAtUtc
) {}
