package com.bb.progress.growth;

import com.bb.progress.baby.BabyService;
import com.bb.progress.common.ApiException;
import com.bb.progress.growth.GrowthDtos.GrowthRecordRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrowthService {

    private final GrowthRecordRepository repository;
    private final BabyService babyService;

    public GrowthService(GrowthRecordRepository repository, BabyService babyService) {
        this.repository = repository;
        this.babyService = babyService;
    }

    @Transactional(readOnly = true)
    public List<GrowthRecord> findAll() {
        return repository.findAllByOrderByMeasuredOnAsc();
    }

    @Transactional
    public GrowthRecord create(GrowthRecordRequest request) {
        validate(request, null);
        return repository.save(new GrowthRecord(request.measuredOn(), request.weightKg(),
                request.heightCm(), request.headCircumferenceCm(), request.note()));
    }

    @Transactional
    public GrowthRecord update(UUID id, GrowthRecordRequest request) {
        GrowthRecord record = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("GROWTH_RECORD_NOT_FOUND", "Growth record not found"));
        validate(request, id);
        record.setMeasuredOn(request.measuredOn());
        record.setWeightKg(request.weightKg());
        record.setHeightCm(request.heightCm());
        record.setHeadCircumferenceCm(request.headCircumferenceCm());
        record.setNote(request.note());
        return repository.save(record);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound("GROWTH_RECORD_NOT_FOUND", "Growth record not found");
        }
        repository.deleteById(id);
    }

    private void validate(GrowthRecordRequest request, UUID existingId) {
        if (!request.hasAnyMeasurement()) {
            throw ApiException.badRequest("NO_MEASUREMENT", "At least one measurement is required");
        }
        LocalDate dob = babyService.get().getDateOfBirth();
        if (request.measuredOn().isBefore(dob)) {
            throw ApiException.badRequest("MEASURED_BEFORE_BIRTH", "Measurement date is before the date of birth");
        }
        repository.findByMeasuredOn(request.measuredOn())
                .filter(existing -> !existing.getId().equals(existingId))
                .ifPresent(existing -> {
                    throw ApiException.badRequest("DUPLICATE_DATE", "A record for this date already exists");
                });
    }
}
