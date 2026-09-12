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

    /** Value of the {@code size} query parameter that asks for a grid-sized copy. */
    public static final String THUMBNAIL = "thumb";

    private PhotoResponses() {
    }

    public static ResponseEntity<Resource> serve(PhotoStorageService storage, String relativePath,
            WebRequest request) {
        return serve(storage, relativePath, null, request);
    }

    public static ResponseEntity<Resource> serve(PhotoStorageService storage, String relativePath,
            String size, WebRequest request) {
        boolean thumbnail = THUMBNAIL.equals(size);
        // The size is part of the identity, or a cached full image would answer a thumbnail request.
        String etag = "\"" + versionOf(relativePath) + (thumbnail ? "-thumb" : "") + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache())
                    .build();
        }
        String contentType = thumbnail ? storage.thumbnailContentType() : storage.contentTypeOf(relativePath);
        Resource body = thumbnail ? storage.loadThumbnail(relativePath) : storage.load(relativePath);
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(contentType))
                .body(body);
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
