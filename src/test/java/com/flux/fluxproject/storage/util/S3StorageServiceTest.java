package com.flux.fluxproject.storage.util;

import com.flux.fluxproject.storage.config.S3Properties;
import com.flux.fluxproject.storage.dto.PresignedUploadRequest;
import com.flux.fluxproject.storage.dto.PresignedUploadResponse;
import com.flux.fluxproject.storage.service.S3StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private ObjectKeyGenerator objectKeyGenerator;

    @InjectMocks
    private S3StorageService storageService;

    @Test
    void shouldGeneratePresignedUploadUrl() throws Exception {

        UUID postId = UUID.randomUUID();

        PresignedUploadRequest request =
                new PresignedUploadRequest(
                        postId,
                        "cat.jpg",
                        "image/jpeg"
                );

        String objectKey =
                "posts/" + postId + "/original/test.jpg";

        URL uploadUrl =
                new URL("https://example.com/upload");

        Instant expiration =
                Instant.now().plus(Duration.ofMinutes(10));

        PresignedPutObjectRequest presignedRequest =
                mock(PresignedPutObjectRequest.class);

        given(objectKeyGenerator.generateObjectKey(
                postId,
                "image/jpeg"
        )).willReturn(objectKey);

        given(s3Properties.bucketName())
                .willReturn("flux-media");

        given(s3Properties.presignedUrlDuration())
                .willReturn(Duration.ofMinutes(10));

        given(presignedRequest.url())
                .willReturn(uploadUrl);

        given(presignedRequest.expiration())
                .willReturn(expiration);

        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presignedRequest);

        PresignedUploadResponse response =
                storageService.generatePresignedUploadUrl(request);

        assertNotNull(response);

        assertEquals(
                objectKey,
                response.objectKey()
        );

        assertEquals(
                uploadUrl,
                response.uploadUrl()
        );

        assertEquals(
                expiration,
                response.expiresAt()
        );

        verify(objectKeyGenerator)
                .generateObjectKey(
                        postId,
                        "image/jpeg"
                );

        verify(s3Presigner)
                .presignPutObject(any(PutObjectPresignRequest.class));
    }
}