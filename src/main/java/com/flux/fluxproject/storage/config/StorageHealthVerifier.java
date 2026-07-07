package com.flux.fluxproject.storage.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StorageHealthVerifier {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Bean
    ApplicationRunner verifyStorageConnection() {

        return args -> {

            log.info("Verifying S3 bucket connectivity...");

            s3Client.headBucket(
                    HeadBucketRequest.builder()
                            .bucket(s3Properties.bucketName())
                            .build()
            );

            log.info("Successfully connected to S3 bucket '{}'.",
                    s3Properties.bucketName());
        };
    }

}