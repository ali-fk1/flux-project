package com.flux.fluxproject.storage.service;

import com.flux.fluxproject.storage.dto.PresignedUploadRequest;
import com.flux.fluxproject.storage.dto.PresignedUploadResponse;
import com.flux.fluxproject.storage.dto.StorageObjectMetadata;

import java.io.InputStream;

public interface StorageService {

    PresignedUploadResponse generatePresignedUploadUrl(
            PresignedUploadRequest request
    );

    StorageObjectMetadata validateObject(String objectKey);

    InputStream download(String objectKey);

    void delete(String objectKey);

}