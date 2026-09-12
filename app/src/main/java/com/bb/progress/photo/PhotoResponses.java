package com.bb.progress.photo;

import com.bb.progress.photo.PhotoStorageService.StoredPhoto;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

/**
 * Serves stored photos with validators so a replaced photo is picked up immediately.
 * Object keys are unique per upload, so the key is a sound ETag; {@code no-cache} makes the
 * browser revalidate rather than serve a stale image from its heuristic cache.
 */
public final class PhotoResponses {

    /** Value of the {@code size} query parameter that asks for a grid-sized copy. */
    public static final String THUMBNAIL = "thumb";

    private PhotoResponses() {
    }

    public static ResponseEntity<Resource> serve(PhotoStorageService storage, String key,
            WebRequest request) {
        return serve(storage, key, null, request);
    }

    public static ResponseEntity<Resource> serve(PhotoStorageService storage, String key,
            String size, WebRequest request) {
        boolean thumbnail = THUMBNAIL.equals(size);
        // The size is part of the identity, or a cached full image would answer a thumbnail request.
        String etag = "\"" + versionOf(key) + (thumbnail ? "-thumb" : "") + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache())
                    .build();
        }
        StoredPhoto photo = thumbnail ? storage.loadThumbnail(key) : storage.load(key);
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .contentLength(photo.contentLength())
                .body(photo.body());
    }

    /** Opaque per-upload version the frontend appends to image URLs to bust its in-memory cache. */
    public static String versionOf(String key) {
        if (key == null) {
            return null;
        }
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
