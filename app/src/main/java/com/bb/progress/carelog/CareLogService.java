package com.bb.progress.carelog;

import com.bb.progress.baby.BabyService;
import com.bb.progress.carelog.CareLogDtos.CareLogCreateRequest;
import com.bb.progress.carelog.CareLogDtos.CareLogUpdateRequest;
import com.bb.progress.common.ApiException;
import com.bb.progress.common.Sgt;
import com.bb.progress.photo.ImageCompressor;
import com.bb.progress.photo.PhotoStorageService;
import java.time.Instant;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareLogService {

    private final CareLogRepository repository;
    private final BabyService babyService;
    private final PhotoStorageService photoStorage;

    public CareLogService(CareLogRepository repository, BabyService babyService,
            PhotoStorageService photoStorage) {
        this.repository = repository;
        this.babyService = babyService;
        this.photoStorage = photoStorage;
    }

    /** Lists entries for the given SGT calendar date (default: today in SGT). */
    @Transactional(readOnly = true)
    public List<CareLog> findByDay(LocalDate date, CareType type) {
        LocalDate day = date != null ? date : Sgt.today();
        Instant from = day.atStartOfDay(Sgt.ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(Sgt.ZONE).toInstant();
        return type == null
                ? repository.findAllByLoggedAtGreaterThanEqualAndLoggedAtLessThanOrderByLoggedAtDesc(from, to)
                : repository.findAllByTypeAndLoggedAtGreaterThanEqualAndLoggedAtLessThanOrderByLoggedAtDesc(
                        type, from, to);
    }

    @Transactional
    public CareLog create(CareLogCreateRequest request) {
        // Same precondition as growth records and milestones: a profile must exist first.
        babyService.get();
        Instant loggedAt = request.loggedAt() != null ? request.loggedAt().toInstant() : Instant.now();
        requireNotFuture(loggedAt);
        return repository.save(new CareLog(request.type(), loggedAt, request.note()));
    }

    @Transactional
    public CareLog update(UUID id, CareLogUpdateRequest request) {
        CareLog log = require(id);
        Instant loggedAt = request.loggedAt().toInstant();
        requireNotFuture(loggedAt);
        log.setLoggedAt(loggedAt);
        log.setNote(request.note());
        return repository.save(log);
    }

    @Transactional
    public void delete(UUID id) {
        CareLog log = require(id);
        repository.delete(log);
        photoStorage.deleteIfExists(log.getPhotoPath());
    }

    /** Photos on log entries are re-encoded to stay small; only the profile photo keeps its original. */
    @Transactional
    public CareLog updatePhoto(UUID id, MultipartFile file) {
        CareLog log = require(id);
        String oldPath = log.getPhotoPath();
        log.setPhotoPath(photoStorage.storeCompressed("care-logs", file, ImageCompressor.ONE_MEGABYTE));
        CareLog saved = repository.save(log);
        photoStorage.deleteIfExists(oldPath);
        return saved;
    }

    @Transactional
    public CareLog removePhoto(UUID id) {
        CareLog log = require(id);
        String oldPath = log.getPhotoPath();
        log.setPhotoPath(null);
        CareLog saved = repository.save(log);
        photoStorage.deleteIfExists(oldPath);
        return saved;
    }

    @Transactional(readOnly = true)
    public String getPhotoPath(UUID id) {
        String path = require(id).getPhotoPath();
        if (path == null) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "No photo on this entry");
        }
        return path;
    }

    private CareLog require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("CARE_LOG_NOT_FOUND", "Care log not found"));
    }

    private void requireNotFuture(Instant loggedAt) {
        // Small grace window absorbs client/server clock skew.
        if (loggedAt.isAfter(Instant.now().plusSeconds(120))) {
            throw ApiException.badRequest("LOGGED_AT_IN_FUTURE", "Log time cannot be in the future");
        }
    }
}
