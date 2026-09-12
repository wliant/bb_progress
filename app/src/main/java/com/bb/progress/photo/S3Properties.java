package com.bb.progress.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object storage settings. The same build runs against MinIO in the compose stack or real AWS S3;
 * only these values differ.
 *
 * @param endpoint          blank for real AWS, a URL for MinIO or another S3-compatible store
 * @param accessKey         blank to fall back to the AWS default credential chain (IAM roles, profiles)
 * @param pathStyle         MinIO needs path-style addressing; AWS prefers virtual-host style
 * @param createBucketIfMissing convenient for a local MinIO, normally false against AWS
 */
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
        String bucket,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        boolean createBucketIfMissing) {

    public boolean hasExplicitCredentials() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    public boolean hasCustomEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
