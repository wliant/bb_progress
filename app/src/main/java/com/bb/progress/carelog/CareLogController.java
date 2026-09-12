package com.bb.progress.carelog;

import com.bb.progress.carelog.CareLogDtos.CareLogCreateRequest;
import com.bb.progress.carelog.CareLogDtos.CareLogResponse;
import com.bb.progress.carelog.CareLogDtos.CareLogUpdateRequest;
import com.bb.progress.photo.PhotoResponses;
import com.bb.progress.photo.PhotoStorageService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/care-logs")
public class CareLogController {

    private final CareLogService service;
    private final PhotoStorageService photoStorage;

    public CareLogController(CareLogService service, PhotoStorageService photoStorage) {
        this.service = service;
        this.photoStorage = photoStorage;
    }

    @GetMapping
    public List<CareLogResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) CareType type) {
        return service.findByDay(date, type).stream().map(CareLogResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CareLogResponse create(@Valid @RequestBody CareLogCreateRequest request) {
        return CareLogResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    public CareLogResponse update(@PathVariable UUID id, @Valid @RequestBody CareLogUpdateRequest request) {
        return CareLogResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PutMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CareLogResponse uploadPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return CareLogResponse.from(service.updatePhoto(id, file));
    }

    @DeleteMapping("/{id}/photo")
    public CareLogResponse removePhoto(@PathVariable UUID id) {
        return CareLogResponse.from(service.removePhoto(id));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> photo(@PathVariable UUID id,
            @RequestParam(required = false) String size, WebRequest request) {
        return PhotoResponses.serve(photoStorage, service.getPhotoPath(id), size, request);
    }
}
