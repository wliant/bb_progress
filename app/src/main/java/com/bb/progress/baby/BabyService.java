package com.bb.progress.baby;

import com.bb.progress.baby.BabyDtos.BabyRequest;
import com.bb.progress.carelog.CareLog;
import com.bb.progress.carelog.CareLogRepository;
import com.bb.progress.common.ApiException;
import com.bb.progress.growth.GrowthRecord;
import com.bb.progress.growth.GrowthRecordRepository;
import com.bb.progress.milestone.MilestoneAchievement;
import com.bb.progress.milestone.MilestoneAchievementRepository;
import com.bb.progress.photo.PhotoStorageService;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BabyService {

    private final BabyRepository repository;
    private final PhotoStorageService photoStorage;
    private final GrowthRecordRepository growthRecords;
    private final MilestoneAchievementRepository achievements;
    private final CareLogRepository careLogs;

    public BabyService(BabyRepository repository, PhotoStorageService photoStorage,
            GrowthRecordRepository growthRecords, MilestoneAchievementRepository achievements,
            CareLogRepository careLogs) {
        this.repository = repository;
        this.photoStorage = photoStorage;
        this.growthRecords = growthRecords;
        this.achievements = achievements;
        this.careLogs = careLogs;
    }

    @Transactional(readOnly = true)
    public Baby get() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> ApiException.notFound("BABY_NOT_FOUND", "Baby profile not created yet"));
    }

    /** The growth record holding the birth measurements, if any were entered. */
    @Transactional(readOnly = true)
    public GrowthRecord getBirthRecord() {
        return growthRecords.findByBirthTrue().orElse(null);
    }

    @Transactional
    public Baby upsert(BabyRequest request) {
        Baby baby = repository.findFirstByOrderByCreatedAtAsc().orElse(null);
        if (baby == null) {
            baby = new Baby(request.name(), request.dateOfBirth(), request.gender());
        } else {
            if (!baby.getDateOfBirth().equals(request.dateOfBirth())) {
                requireDateOfBirthFitsExistingRecords(request.dateOfBirth());
            }
            baby.setName(request.name());
            baby.setDateOfBirth(request.dateOfBirth());
            baby.setGender(request.gender());
        }
        baby.setTimeOfBirth(request.timeOfBirth());
        baby.setGestationalAgeDays(request.gestationalAgeDays());
        Baby saved = repository.save(baby);
        applyBirthMeasurements(saved.getDateOfBirth(), request);
        return saved;
    }

    /**
     * Birth measurements are kept as the growth record dated at birth, so they plot as the
     * first point on the chart. The record follows the date of birth when it is corrected.
     */
    private void applyBirthMeasurements(LocalDate dateOfBirth, BabyRequest request) {
        GrowthRecord record = growthRecords.findByBirthTrue()
                // Adopt a record already sitting on the birth date rather than colliding with it.
                .or(() -> growthRecords.findByMeasuredOn(dateOfBirth))
                .orElse(null);

        if (!request.hasBirthMeasurements()) {
            if (record != null && record.isBirth()) {
                growthRecords.delete(record);
            }
            return;
        }

        if (record == null) {
            record = new GrowthRecord(dateOfBirth, request.birthWeightKg(), request.birthLengthCm(),
                    request.birthHeadCircumferenceCm(), null);
        } else {
            record.setMeasuredOn(dateOfBirth);
            record.setWeightKg(request.birthWeightKg());
            record.setHeightCm(request.birthLengthCm());
            record.setHeadCircumferenceCm(request.birthHeadCircumferenceCm());
        }
        record.setBirth(true);
        growthRecords.save(record);
    }

    /**
     * Records are already refused when dated before birth; without this, moving the birth date
     * forward past existing records would leave them stranded at a negative age on the chart.
     */
    private void requireDateOfBirthFitsExistingRecords(LocalDate dateOfBirth) {
        growthRecords.findFirstByBirthFalseOrderByMeasuredOnAsc()
                .filter(record -> record.getMeasuredOn().isBefore(dateOfBirth))
                .ifPresent(record -> {
                    throw ApiException.badRequest("DOB_AFTER_RECORDS",
                            "Date of birth is after an existing growth record (" + record.getMeasuredOn() + ")");
                });
        achievements.findFirstByOrderByAchievedOnAsc()
                .filter(achievement -> achievement.getAchievedOn().isBefore(dateOfBirth))
                .ifPresent(achievement -> {
                    throw ApiException.badRequest("DOB_AFTER_RECORDS",
                            "Date of birth is after an achieved milestone (" + achievement.getAchievedOn() + ")");
                });
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

    /**
     * Full reset: clears the profile and everything recorded against it, photos included.
     * The rows are deleted in bulk rather than through their services, so every photo has to be
     * removed explicitly here — missing one leaks the file onto the volume with nothing pointing
     * at it.
     */
    @Transactional
    public void resetAll() {
        repository.findFirstByOrderByCreatedAtAsc()
                .ifPresent(baby -> photoStorage.deleteIfExists(baby.getPhotoPath()));
        achievements.findAll().stream()
                .map(MilestoneAchievement::getPhotoPath)
                .forEach(photoStorage::deleteIfExists);
        careLogs.findAll().stream()
                .map(CareLog::getPhotoPath)
                .forEach(photoStorage::deleteIfExists);
        careLogs.deleteAll();
        growthRecords.deleteAll();
        achievements.deleteAll();
        repository.deleteAll();
    }
}
