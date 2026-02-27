package com.flux.fluxproject.model;

import java.time.Instant;
import java.util.UUID;

public record PostCursor(
        Instant scheduledAt,
        UUID id
) {}
