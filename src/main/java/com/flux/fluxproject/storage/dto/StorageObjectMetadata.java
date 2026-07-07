package com.flux.fluxproject.storage.dto;

public record StorageObjectMetadata(
        String objectKey,
        long size,
        String contentType,
        String eTag
) {}