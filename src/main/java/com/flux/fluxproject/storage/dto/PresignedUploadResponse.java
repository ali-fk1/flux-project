package com.flux.fluxproject.storage.dto;

import java.net.URL;
import java.time.Instant;

public record PresignedUploadResponse(

        String objectKey,

        URL uploadUrl,

        Instant expiresAt

) {
}