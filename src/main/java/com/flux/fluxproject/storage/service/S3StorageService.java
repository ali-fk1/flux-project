package com.flux.fluxproject.storage.service;

import com.flux.fluxproject.storage.config.S3Properties;
import com.flux.fluxproject.storage.dto.PresignedUploadRequest;
import com.flux.fluxproject.storage.dto.PresignedUploadResponse;
import com.flux.fluxproject.storage.dto.StorageObjectMetadata;
import com.flux.fluxproject.storage.util.ObjectKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final ObjectKeyGenerator objectKeyGenerator;

    @Override
    public PresignedUploadResponse generatePresignedUploadUrl(PresignedUploadRequest request) {

        String objectKey = objectKeyGenerator.generateObjectKey(
                request.postId(),
                request.contentType()
        );

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(objectKey)
                .contentType(request.contentType())
                .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(s3Properties.presignedUrlDuration())
                        .putObjectRequest(putObjectRequest)
                        .build();

        PresignedPutObjectRequest presigned =
                s3Presigner.presignPutObject(presignRequest);

        return new PresignedUploadResponse(
                objectKey,
                presigned.url(),
                presigned.expiration()
        );
    }

    @Override
    public StorageObjectMetadata validateObject(String objectKey) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public InputStream download(String objectKey) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void delete(String objectKey) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}