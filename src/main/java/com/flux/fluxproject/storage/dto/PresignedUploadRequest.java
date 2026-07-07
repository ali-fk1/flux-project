package com.flux.fluxproject.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PresignedUploadRequest(

        @NotNull
        UUID postId,

        @NotBlank
        String originalFilename,

        @NotBlank
        String contentType

) {
}