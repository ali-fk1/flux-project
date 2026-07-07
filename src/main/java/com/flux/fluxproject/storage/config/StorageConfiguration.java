package com.flux.fluxproject.storage.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class StorageConfiguration {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(AwsProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        properties.accessId(),
                        properties.secretAccessKey()
                )
        );
    }

    @Bean
    public S3Client s3Client(
            AwsCredentialsProvider credentialsProvider,
            AwsProperties properties) {

        return S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(properties.defaultRegion()))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            AwsCredentialsProvider credentialsProvider,
            AwsProperties properties) {

        return S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(properties.defaultRegion()))
                .build();
    }
}
