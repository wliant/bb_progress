package com.bb.progress.headshape;

import com.bb.progress.baby.BabyService;
import com.bb.progress.common.ApiException;
import com.bb.progress.common.Sgt;
import com.bb.progress.headshape.HeadShapeDtos.HeadShapeEntryRequest;
import com.bb.progress.headshape.HeadShapeDtos.HeadShapeEntryResponse;
import com.bb.progress.media.Media;
import com.bb.progress.media.MediaService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HeadShapeService {

    private final HeadShapeEntryRepository repository;
    private final BabyService babyService;
    private final MediaService mediaService;

    public HeadShapeService(HeadShapeEntryRepository repository, BabyService babyService,
            MediaService mediaService) {
        this.repository = repository;
        this.babyService = babyService;
        this.mediaService = mediaService;
    }

    /** Every day, newest first — the page exists to compare across days, not to review one. */
    @Transactional(readOnly = true)
    public List<HeadShapeEntryResponse> list() {
        List<HeadShapeEntry> entries = repository.findAllByOrderByRecordedOnDesc();
        // One query for every entry's photos rather than one per entry.
        Map<UUID, List<Media>> byEntry = mediaService
                .forHeadShapeEntries(entries.stream().map(HeadShapeEntry::getId).toList()).stream()
                .collect(Collectors.groupingBy(Media::getHeadShapeEntryId));
        return entries.stream()
                .map(entry -> HeadShapeEntryResponse.from(entry,
                        MediaService.views(byEntry.getOrDefault(entry.getId(), List.of()))))
                .toList();
    }

    /**
     * Creates the day, or updates the note of the day that already exists. The caller adding
     * photos to a date does not have to know which, and the unique constraint on
     * {@code recorded_on} is never the thing that tells them.
     */
    @Transactional
    public HeadShapeEntryResponse save(HeadShapeEntryRequest request) {
        babyService.get();
        requireNotFuture(request.recordedOn());
        HeadShapeEntry entry = repository.findByRecordedOn(request.recordedOn())
                .orElseGet(() -> new HeadShapeEntry(request.recordedOn(), null));
        entry.setNote(request.note());
        return withMedia(repository.save(entry));
    }

    @Transactional
    public HeadShapeEntryResponse update(UUID id, HeadShapeEntryRequest request) {
        HeadShapeEntry entry = require(id);
        requireNotFuture(request.recordedOn());
        entry.setRecordedOn(request.recordedOn());
        entry.setNote(request.note());
        return withMedia(repository.save(entry));
    }

    @Transactional
    public void delete(UUID id) {
        HeadShapeEntry entry = require(id);
        // The rows cascade with the entry, but the stored objects have to go explicitly.
        mediaService.deleteObjectsFor(mediaService.forHeadShapeEntry(id));
        repository.delete(entry);
    }

    /** The entry for a date, created on the spot — the upload path's "pick a day and go". */
    @Transactional
    public HeadShapeEntry getOrCreate(LocalDate recordedOn) {
        babyService.get();
        requireNotFuture(recordedOn);
        return repository.findByRecordedOn(recordedOn)
                .orElseGet(() -> repository.save(new HeadShapeEntry(recordedOn, null)));
    }

    @Transactional(readOnly = true)
    public HeadShapeEntry require(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException
                .notFound("HEAD_SHAPE_ENTRY_NOT_FOUND", "Head shape entry not found"));
    }

    private HeadShapeEntryResponse withMedia(HeadShapeEntry entry) {
        return HeadShapeEntryResponse.from(entry,
                MediaService.views(mediaService.forHeadShapeEntry(entry.getId())));
    }

    private void requireNotFuture(LocalDate recordedOn) {
        if (recordedOn.isAfter(Sgt.today())) {
            throw ApiException.badRequest("RECORDED_ON_IN_FUTURE",
                    "Head shape cannot be recorded for a future date");
        }
    }
}
