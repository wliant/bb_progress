package com.bb.progress.media;

import com.bb.progress.common.ApiException;
import com.bb.progress.media.MediaDtos.MediaView;
import com.bb.progress.photo.ImageCompressor;
import com.bb.progress.photo.PhotoStorageService;
import com.bb.progress.photo.PhotoStorageService.StoredPhoto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {

    /** Only types the browser can play back without help from the app. */
    private static final Map<String, MediaKind> KIND_BY_TYPE = Map.ofEntries(
            Map.entry("image/jpeg", MediaKind.PHOTO),
            Map.entry("image/png", MediaKind.PHOTO),
            Map.entry("image/webp", MediaKind.PHOTO),
            Map.entry("video/mp4", MediaKind.VIDEO),
            Map.entry("video/quicktime", MediaKind.VIDEO),
            Map.entry("video/webm", MediaKind.VIDEO),
            Map.entry("audio/mpeg", MediaKind.AUDIO),
            Map.entry("audio/mp4", MediaKind.AUDIO),
            Map.entry("audio/aac", MediaKind.AUDIO),
            Map.entry("audio/wav", MediaKind.AUDIO),
            Map.entry("audio/x-wav", MediaKind.AUDIO),
            Map.entry("audio/ogg", MediaKind.AUDIO),
            Map.entry("audio/webm", MediaKind.AUDIO));

    private static final Map<String, String> EXTENSION_BY_TYPE = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("video/mp4", "mp4"),
            Map.entry("video/quicktime", "mov"),
            Map.entry("video/webm", "webm"),
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/aac", "aac"),
            Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("audio/ogg", "ogg"),
            Map.entry("audio/webm", "weba"));

    private final MediaRepository repository;
    private final PhotoStorageService storage;

    public MediaService(MediaRepository repository, PhotoStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<Media> forCareLog(UUID careLogId) {
        return repository.findAllByCareLogIdOrderBySortOrderAsc(careLogId);
    }

    @Transactional(readOnly = true)
    public List<Media> forCareLogs(List<UUID> careLogIds) {
        return careLogIds.isEmpty() ? List.of()
                : repository.findAllByCareLogIdInOrderBySortOrderAsc(careLogIds);
    }

    @Transactional(readOnly = true)
    public List<Media> forMilestones(List<String> milestoneIds) {
        return milestoneIds.isEmpty() ? List.of()
                : repository.findAllByMilestoneIdInOrderBySortOrderAsc(milestoneIds);
    }

    @Transactional(readOnly = true)
    public List<Media> forMilestone(String milestoneId) {
        return repository.findAllByMilestoneIdOrderBySortOrderAsc(milestoneId);
    }

    @Transactional
    public List<MediaView> addToCareLog(UUID careLogId, List<MultipartFile> files) {
        int next = nextSortOrder(repository.findAllByCareLogIdOrderBySortOrderAsc(careLogId));
        for (MultipartFile file : files) {
            Stored stored = store(file);
            repository.save(Media.forCareLog(careLogId, stored.kind(), stored.key(),
                    stored.contentType(), next++));
        }
        return views(repository.findAllByCareLogIdOrderBySortOrderAsc(careLogId));
    }

    @Transactional
    public List<MediaView> addToMilestone(String milestoneId, List<MultipartFile> files) {
        int next = nextSortOrder(repository.findAllByMilestoneIdOrderBySortOrderAsc(milestoneId));
        for (MultipartFile file : files) {
            Stored stored = store(file);
            repository.save(Media.forMilestone(milestoneId, stored.kind(), stored.key(),
                    stored.contentType(), next++));
        }
        return views(repository.findAllByMilestoneIdOrderBySortOrderAsc(milestoneId));
    }

    @Transactional
    public void delete(UUID mediaId) {
        Media media = repository.findById(mediaId)
                .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "Attachment not found"));
        repository.delete(media);
        storage.deleteIfExists(media.getObjectKey());
    }

    /** Removes the stored objects for rows that are about to be deleted with their owner. */
    @Transactional
    public void deleteObjectsFor(List<Media> media) {
        media.stream().map(Media::getObjectKey).forEach(storage::deleteIfExists);
    }

    @Transactional(readOnly = true)
    public StoredPhoto content(UUID mediaId, String size) {
        Media media = repository.findById(mediaId)
                .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "Attachment not found"));
        if (media.getKind() == MediaKind.PHOTO && "thumb".equals(size)) {
            return storage.loadThumbnail(media.getObjectKey());
        }
        StoredPhoto stored = storage.load(media.getObjectKey());
        // The stored content type is authoritative; the key's extension is only a hint.
        return new StoredPhoto(stored.body(), stored.contentLength(), media.getContentType());
    }

    @Transactional(readOnly = true)
    public String objectKeyOf(UUID mediaId) {
        return repository.findById(mediaId)
                .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "Attachment not found"))
                .getObjectKey();
    }

    public static List<MediaView> views(List<Media> media) {
        return media.stream().map(MediaView::from).toList();
    }

    private record Stored(MediaKind kind, String key, String contentType) {
    }

    private Stored store(MultipartFile file) {
        // Recorders report types like "audio/webm;codecs=opus"; the parameters are not part
        // of the media type we key on.
        String contentType = baseContentType(file.getContentType());
        MediaKind kind = contentType == null ? null : KIND_BY_TYPE.get(contentType);
        if (kind == null) {
            throw ApiException.badRequest("UNSUPPORTED_MEDIA_TYPE",
                    "Only photos, videos and voice recordings are supported");
        }
        if (file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Uploaded file is empty");
        }
        if (kind == MediaKind.PHOTO) {
            // Photos are re-encoded to stay small; video and audio are kept as recorded
            // because the app has no transcoder.
            return new Stored(kind, storage.storeCompressed("media", file, ImageCompressor.ONE_MEGABYTE),
                    "image/jpeg");
        }
        return new Stored(kind, storage.storeAs("media", file, EXTENSION_BY_TYPE.get(contentType)),
                contentType);
    }

    static String baseContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int parameters = contentType.indexOf(';');
        return (parameters < 0 ? contentType : contentType.substring(0, parameters))
                .trim().toLowerCase();
    }

    private static int nextSortOrder(List<Media> existing) {
        return existing.stream().mapToInt(Media::getSortOrder).max().orElse(-1) + 1;
    }
}
