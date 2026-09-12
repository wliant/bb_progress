package com.bb.progress.media;

import com.bb.progress.media.MediaDtos.MediaView;
import com.bb.progress.photo.PhotoResponses;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {

    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @GetMapping("/api/care-logs/{id}/media")
    public List<MediaView> careLogMedia(@PathVariable UUID id) {
        return MediaService.views(service.forCareLog(id));
    }

    @PostMapping(path = "/api/care-logs/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<MediaView> addCareLogMedia(@PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files) {
        return service.addToCareLog(id, files);
    }

    @GetMapping("/api/milestones/{definitionId}/achievement/media")
    public List<MediaView> milestoneMedia(@PathVariable String definitionId) {
        return MediaService.views(service.forMilestone(definitionId));
    }

    @PostMapping(path = "/api/milestones/{definitionId}/achievement/media",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<MediaView> addMilestoneMedia(@PathVariable String definitionId,
            @RequestParam("files") List<MultipartFile> files) {
        return service.addToMilestone(definitionId, files);
    }

    @DeleteMapping("/api/media/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID mediaId) {
        service.delete(mediaId);
    }

    /**
     * Streams the attachment. Photos honour {@code ?size=thumb}; video and audio are served whole,
     * which is enough for the inline players a personal app needs.
     */
    @GetMapping("/api/media/{mediaId}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID mediaId,
            @RequestParam(required = false) String size, WebRequest request) {
        String etag = "\"" + PhotoResponses.versionOf(service.objectKeyOf(mediaId))
                + ("thumb".equals(size) ? "-thumb" : "") + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag).cacheControl(CacheControl.noCache()).build();
        }
        var stored = service.content(mediaId, size);
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.contentLength())
                .body(stored.body());
    }
}
