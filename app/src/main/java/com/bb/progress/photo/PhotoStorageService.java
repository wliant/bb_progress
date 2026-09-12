package com.bb.progress.photo;

import com.bb.progress.common.ApiException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Photo bytes live in S3-compatible object storage; the database only ever holds the object key.
 * Objects are streamed back through the API rather than exposed directly, so URLs, cache
 * validators and the error contract stay in one place.
 */
@Service
public class PhotoStorageService {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorageService.class);

    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private static final int THUMBNAIL_MAX_EDGE = 320;
    private static final long THUMBNAIL_MAX_BYTES = 120_000L;

    /** A stored object ready to be written to a response. */
    public record StoredPhoto(Resource body, long contentLength, String contentType) {
    }

    private final S3Client s3;
    private final S3Properties properties;
    private final ImageCompressor compressor;

    public PhotoStorageService(S3Client s3, S3Properties properties, ImageCompressor compressor) {
        this.s3 = s3;
        this.properties = properties;
        this.compressor = compressor;
    }

    @PostConstruct
    void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception e) {
            // Only a genuinely absent bucket is worth creating; anything else (bad credentials,
            // no permission) must surface rather than be masked by a failing CreateBucket.
            boolean missing = e instanceof NoSuchBucketException || e.statusCode() == 404;
            if (!missing || !properties.createBucketIfMissing()) {
                throw e;
            }
            log.info("Creating object storage bucket {}", properties.bucket());
            s3.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
        }
    }

    /** Stores the upload byte-for-byte. Used for the profile photo, which keeps full quality. */
    public String store(String prefix, MultipartFile file) {
        String extension = validate(file);
        String key = newKey(prefix, extension);
        try (InputStream in = file.getInputStream()) {
            put(key, contentTypeOf(key), RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store photo", e);
        }
        return key;
    }

    /**
     * Re-encodes the upload to a JPEG of at most {@code maxBytes} before storing, so a photo
     * straight off a phone can be accepted without keeping megabytes per log entry.
     */
    public String storeCompressed(String prefix, MultipartFile file, long maxBytes) {
        validate(file);
        byte[] source;
        try {
            source = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload", e);
        }
        byte[] compressed = compressor.compress(source, maxBytes);
        String key = newKey(prefix, "jpg");
        put(key, "image/jpeg", RequestBody.fromBytes(compressed));
        return key;
    }

    public StoredPhoto load(String key) {
        return get(key, contentTypeOf(key));
    }

    /**
     * A reduced copy for grid tiles, created on first request and kept as a sibling object so a
     * gallery does not pull a megabyte per tile.
     */
    public StoredPhoto loadThumbnail(String key) {
        String thumbnailKey = thumbnailKey(key);
        if (!exists(thumbnailKey)) {
            byte[] original = readAll(key);
            put(thumbnailKey, "image/jpeg",
                    RequestBody.fromBytes(compressor.compress(original, THUMBNAIL_MAX_BYTES, THUMBNAIL_MAX_EDGE)));
        }
        return get(thumbnailKey, "image/jpeg");
    }

    /** Thumbnails are always JPEG regardless of the original's type. */
    public String thumbnailContentType() {
        return "image/jpeg";
    }

    public void deleteIfExists(String key) {
        if (key == null) {
            return;
        }
        delete(key);
        // The cached thumbnail must go with it, or a stale tile outlives the photo.
        delete(thumbnailKey(key));
    }

    public String contentTypeOf(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String validate(MultipartFile file) {
        String contentType = file.getContentType();
        String extension = contentType == null ? null : EXTENSION_BY_TYPE.get(contentType);
        if (extension == null) {
            throw ApiException.badRequest("UNSUPPORTED_PHOTO_TYPE",
                    "Only JPEG, PNG and WebP photos are supported");
        }
        if (file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Uploaded file is empty");
        }
        return extension;
    }

    private static String newKey(String prefix, String extension) {
        return prefix + "/" + UUID.randomUUID() + "." + extension;
    }

    static String thumbnailKey(String key) {
        int dot = key.lastIndexOf('.');
        return (dot < 0 ? key : key.substring(0, dot)) + "_thumb.jpg";
    }

    private void put(String key, String contentType, RequestBody body) {
        s3.putObject(PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build(), body);
    }

    private StoredPhoto get(String key, String contentType) {
        try {
            ResponseInputStream<GetObjectResponse> stream = s3.getObject(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            return new StoredPhoto(new InputStreamResource(stream),
                    stream.response().contentLength(), contentType);
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "Photo not found");
        }
    }

    private byte[] readAll(String key) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "Photo not found");
        }
    }

    private boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }
}
