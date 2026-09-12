package com.bb.progress.photo;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

/**
 * Serves stored photos with validators so a replaced photo is picked up immediately.
 * Stored filenames are unique per upload, so the filename is a sound ETag; {@code no-cache}
 * makes the browser revalidate rather than serve a stale image from its heuristic cache.
 */
public final class PhotoResponses {

    private PhotoResponses() {
    }

    public static ResponseEntity<Resource> serve(PhotoStorageService storage, String relativePath,
            WebRequest request) {
        String etag = "\"" + versionOf(relativePath) + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache())
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(storage.contentTypeOf(relativePath)))
                .body(storage.load(relativePath));
    }

    /** Opaque per-upload version the frontend appends to image URLs to bust its in-memory cache. */
    public static String versionOf(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
