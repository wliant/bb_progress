package com.bb.progress.baby;

import com.bb.progress.baby.BabyDtos.BabyRequest;
import com.bb.progress.common.ApiException;
import com.bb.progress.photo.PhotoStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BabyService {

    private final BabyRepository repository;
    private final PhotoStorageService photoStorage;

    public BabyService(BabyRepository repository, PhotoStorageService photoStorage) {
        this.repository = repository;
        this.photoStorage = photoStorage;
    }

    @Transactional(readOnly = true)
    public Baby get() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> ApiException.notFound("BABY_NOT_FOUND", "Baby profile not created yet"));
    }

    @Transactional
    public Baby upsert(BabyRequest request) {
        Baby baby = repository.findFirstByOrderByCreatedAtAsc().orElse(null);
        if (baby == null) {
            baby = new Baby(request.name(), request.dateOfBirth(), request.gender());
        } else {
            baby.setName(request.name());
            baby.setDateOfBirth(request.dateOfBirth());
            baby.setGender(request.gender());
        }
        return repository.save(baby);
    }

    @Transactional
    public void updatePhoto(MultipartFile file) {
        Baby baby = get();
        String oldPath = baby.getPhotoPath();
        baby.setPhotoPath(photoStorage.store("baby", file));
        repository.save(baby);
        photoStorage.deleteIfExists(oldPath);
    }

    @Transactional(readOnly = true)
    public String getPhotoPath() {
        String path = get().getPhotoPath();
        if (path == null) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "No profile photo uploaded");
        }
        return path;
    }
}
