package com.flux.fluxproject.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        String bucketName,
        Duration presignedUrlDuration
) {
}