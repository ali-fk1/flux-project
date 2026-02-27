package com.flux.fluxproject.model;

import com.flux.fluxproject.domain.PostStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostViewResponse(
        UUID id,
        String content,
        PostStatus status,
        Instant scheduledAtUtc,
        List<String> mediaUrls
) {}

