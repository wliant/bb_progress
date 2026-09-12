package com.bb.progress.photo;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    S3Client s3Client(S3Properties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .forcePathStyle(properties.pathStyle());

        if (properties.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        builder.credentialsProvider(properties.hasExplicitCredentials()
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()))
                // No keys configured: let the SDK find an IAM role, profile or environment.
                : DefaultCredentialsProvider.create());

        return builder.build();
    }
}
