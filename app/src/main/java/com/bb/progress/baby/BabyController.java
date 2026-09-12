package com.bb.progress.baby;

import com.bb.progress.baby.BabyDtos.BabyRequest;
import com.bb.progress.baby.BabyDtos.BabyResponse;
import com.bb.progress.photo.PhotoStorageService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/baby")
public class BabyController {

    private final BabyService service;
    private final PhotoStorageService photoStorage;

    public BabyController(BabyService service, PhotoStorageService photoStorage) {
        this.service = service;
        this.photoStorage = photoStorage;
    }

    @GetMapping
    public BabyResponse get() {
        return BabyResponse.from(service.get());
    }

    @PutMapping
    public BabyResponse upsert(@Valid @RequestBody BabyRequest request) {
        return BabyResponse.from(service.upsert(request));
    }

    @PutMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BabyResponse uploadPhoto(@RequestParam("file") MultipartFile file) {
        service.updatePhoto(file);
        return BabyResponse.from(service.get());
    }

    @GetMapping("/photo")
    public ResponseEntity<Resource> photo() {
        String path = service.getPhotoPath();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photoStorage.contentTypeOf(path)))
                .body(photoStorage.load(path));
    }
}
