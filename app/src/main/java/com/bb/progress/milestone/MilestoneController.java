package com.bb.progress.milestone;

import com.bb.progress.milestone.MilestoneDtos.AchievementRequest;
import com.bb.progress.milestone.MilestoneDtos.AchievementView;
import com.bb.progress.milestone.MilestoneDtos.AgeGroupView;
import com.bb.progress.photo.PhotoStorageService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/milestones")
public class MilestoneController {

    private final MilestoneService service;
    private final PhotoStorageService photoStorage;

    public MilestoneController(MilestoneService service, PhotoStorageService photoStorage) {
        this.service = service;
        this.photoStorage = photoStorage;
    }

    @GetMapping
    public List<AgeGroupView> list() {
        return service.listGroupedByAge();
    }

    @PutMapping("/{definitionId}/achievement")
    public AchievementView setAchievement(@PathVariable String definitionId,
            @Valid @RequestBody AchievementRequest request) {
        return service.setAchievement(definitionId, request);
    }

    @DeleteMapping("/{definitionId}/achievement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAchievement(@PathVariable String definitionId) {
        service.removeAchievement(definitionId);
    }

    @PutMapping(path = "/{definitionId}/achievement/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AchievementView uploadPhoto(@PathVariable String definitionId,
            @RequestParam("file") MultipartFile file) {
        service.updatePhoto(definitionId, file);
        return service.listGroupedByAge().stream()
                .flatMap(group -> group.milestones().stream())
                .filter(m -> m.id().equals(definitionId))
                .findFirst()
                .map(MilestoneDtos.MilestoneView::achievement)
                .orElseThrow();
    }

    @GetMapping("/{definitionId}/achievement/photo")
    public ResponseEntity<Resource> photo(@PathVariable String definitionId) {
        String path = service.getPhotoPath(definitionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photoStorage.contentTypeOf(path)))
                .body(photoStorage.load(path));
    }
}
