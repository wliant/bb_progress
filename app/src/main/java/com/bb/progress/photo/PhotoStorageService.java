package com.bb.progress.photo;

import com.bb.progress.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoStorageService {

    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private static final int THUMBNAIL_MAX_EDGE = 320;
    private static final long THUMBNAIL_MAX_BYTES = 120_000L;

    private final Path baseDir;
    private final ImageCompressor compressor;

    public PhotoStorageService(@Value("${app.photo-dir}") String photoDir, ImageCompressor compressor) {
        this.baseDir = Path.of(photoDir);
        this.compressor = compressor;
    }

    /** Stores the upload byte-for-byte. Used for the profile photo, which keeps full quality. */
    public String store(String subdir, MultipartFile file) {
        String extension = validate(file);
        try (InputStream in = file.getInputStream()) {
            return write(subdir, extension, target -> Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store photo", e);
        }
    }

    /**
     * Re-encodes the upload to a JPEG of at most {@code maxBytes} before storing, so a photo
     * straight off a phone can be accepted without keeping megabytes per log entry.
     */
    public String storeCompressed(String subdir, MultipartFile file, long maxBytes) {
        validate(file);
        byte[] source;
        try {
            source = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload", e);
        }
        byte[] compressed = compressor.compress(source, maxBytes);
        return write(subdir, "jpg", target -> Files.write(target, compressed));
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

    private interface Writer {
        void write(Path target) throws IOException;
    }

    private String write(String subdir, String extension, Writer writer) {
        String relativePath = subdir + "/" + UUID.randomUUID() + "." + extension;
        Path target = baseDir.resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            writer.write(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store photo", e);
        }
        return relativePath;
    }

    /**
     * A reduced copy for grid tiles, generated on first request and cached beside the original so
     * a gallery does not pull a megabyte per tile.
     */
    public Resource loadThumbnail(String relativePath) {
        Path original = resolve(relativePath);
        Path thumbnail = original.resolveSibling(thumbnailName(relativePath));
        if (!Files.exists(thumbnail)) {
            try {
                byte[] source = Files.readAllBytes(original);
                Files.write(thumbnail, compressor.compress(source, THUMBNAIL_MAX_BYTES, THUMBNAIL_MAX_EDGE));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create thumbnail", e);
            }
        }
        return toResource(thumbnail);
    }

    private static String thumbnailName(String relativePath) {
        String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return (dot < 0 ? fileName : fileName.substring(0, dot)) + "_thumb.jpg";
    }

    /** Thumbnails are always JPEG regardless of the original's type. */
    public String thumbnailContentType() {
        return "image/jpeg";
    }

    public Resource load(String relativePath) {
        return toResource(resolve(relativePath));
    }

    private Path resolve(String relativePath) {
        Path path = baseDir.resolve(relativePath).normalize();
        if (!path.startsWith(baseDir.normalize()) || !Files.exists(path)) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "Photo not found");
        }
        return path;
    }

    private Resource toResource(Path path) {
        try {
            return new UrlResource(path.toUri());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load photo", e);
        }
    }

    public String contentTypeOf(String relativePath) {
        String lower = relativePath.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public void deleteIfExists(String relativePath) {
        if (relativePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(baseDir.resolve(relativePath));
            // The cached thumbnail must go with it, or a stale tile outlives the photo.
            Files.deleteIfExists(baseDir.resolve(relativePath).resolveSibling(thumbnailName(relativePath)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete photo", e);
        }
    }
}
