package com.bb.progress.carelog;

import com.bb.progress.baby.BabyService;
import com.bb.progress.carelog.CareLogDtos.CareLogCreateRequest;
import com.bb.progress.carelog.CareLogDtos.CareLogResponse;
import com.bb.progress.carelog.CareLogDtos.CareLogUpdateRequest;
import com.bb.progress.common.ApiException;
import com.bb.progress.common.Sgt;
import com.bb.progress.media.Media;
import com.bb.progress.media.MediaService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareLogService {

    private final CareLogRepository repository;
    private final BabyService babyService;
    private final MediaService mediaService;

    public CareLogService(CareLogRepository repository, BabyService babyService,
            MediaService mediaService) {
        this.repository = repository;
        this.babyService = babyService;
        this.mediaService = mediaService;
    }

    /** Lists entries for the given SGT calendar date (default: today in SGT). */
    @Transactional(readOnly = true)
    public List<CareLogResponse> findByDay(LocalDate date, CareType type) {
        LocalDate day = date != null ? date : Sgt.today();
        Instant from = day.atStartOfDay(Sgt.ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(Sgt.ZONE).toInstant();
        List<CareLog> logs = type == null
                ? repository.findAllByLoggedAtGreaterThanEqualAndLoggedAtLessThanOrderByLoggedAtDesc(from, to)
                : repository.findAllByTypeAndLoggedAtGreaterThanEqualAndLoggedAtLessThanOrderByLoggedAtDesc(
                        type, from, to);
        // One query for every entry's attachments rather than one per entry.
        Map<UUID, List<Media>> byLog = mediaService
                .forCareLogs(logs.stream().map(CareLog::getId).toList()).stream()
                .collect(Collectors.groupingBy(Media::getCareLogId));
        return logs.stream()
                .map(log -> CareLogResponse.from(log,
                        MediaService.views(byLog.getOrDefault(log.getId(), List.of()))))
                .toList();
    }

    @Transactional
    public CareLogResponse create(CareLogCreateRequest request) {
        // Same precondition as growth records and milestones: a profile must exist first.
        babyService.get();
        Instant loggedAt = request.loggedAt() != null ? request.loggedAt().toInstant() : Instant.now();
        requireNotFuture(loggedAt);
        CareLog log = new CareLog(request.type(), loggedAt, request.note());
        applyDetail(log, request);
        return CareLogResponse.from(repository.save(log), List.of());
    }

    @Transactional
    public CareLogResponse update(UUID id, CareLogUpdateRequest request) {
        CareLog log = require(id);
        Instant loggedAt = request.loggedAt().toInstant();
        requireNotFuture(loggedAt);
        log.setLoggedAt(loggedAt);
        log.setNote(request.note());
        applyDetail(log, request);
        return withMedia(repository.save(log));
    }

    @Transactional
    public void delete(UUID id) {
        CareLog log = require(id);
        // The rows cascade with the entry, but the stored objects have to go explicitly.
        mediaService.deleteObjectsFor(mediaService.forCareLog(id));
        repository.delete(log);
    }

    @Transactional(readOnly = true)
    public CareLogResponse withMedia(CareLog log) {
        return CareLogResponse.from(log, MediaService.views(mediaService.forCareLog(log.getId())));
    }

    @Transactional(readOnly = true)
    public CareLog require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("CARE_LOG_NOT_FOUND", "Care log not found"));
    }

    /**
     * Ends an open sleep at the current time. A dedicated endpoint rather than a plain
     * update so the common case — tapping "woke up" — cannot accidentally rewrite the
     * start time it was read from.
     */
    @Transactional
    public CareLogResponse endSleep(UUID id) {
        CareLog log = require(id);
        if (log.getType() != CareType.SLEEP) {
            throw ApiException.badRequest("CARE_LOG_NOT_SLEEP", "Only a sleep entry can be ended");
        }
        if (log.getEndedAt() != null) {
            throw ApiException.conflict("SLEEP_ALREADY_ENDED", "This sleep already has an end time");
        }
        Instant now = Instant.now();
        if (log.getStartedAt() != null && !now.isAfter(log.getStartedAt())) {
            throw ApiException.badRequest("SLEEP_WINDOW_INVALID", "Sleep must end after it started");
        }
        log.setEndedAt(now);
        return withMedia(repository.save(log));
    }

    /**
     * Copies the detail fields that belong to this entry's type and rejects any sent for
     * another type. The same rule is a CHECK constraint in V10 — this layer exists to turn
     * it into the documented error contract rather than a 500 from the database.
     */
    private void applyDetail(CareLog log, CareLogDtos.CareDetail detail) {
        switch (log.getType()) {
            case FEEDING -> {
                rejectForeign(detail.startedAt() != null || detail.endedAt() != null
                        || detail.condition() != null || detail.wet() != null, CareType.FEEDING);
                log.setFeedMethod(detail.method());
                log.setAmountMl(detail.amountMl());
                log.setDurationMin(detail.durationMin());
            }
            case SLEEP -> {
                rejectForeign(detail.method() != null || detail.amountMl() != null
                        || detail.durationMin() != null || detail.condition() != null
                        || detail.wet() != null, CareType.SLEEP);
                // The window drives the entry's place in the day, so the two stay in step.
                Instant startedAt = detail.startedAt() != null
                        ? detail.startedAt().toInstant()
                        : log.getLoggedAt();
                Instant endedAt = detail.endedAt() != null ? detail.endedAt().toInstant() : null;
                requireNotFuture(startedAt);
                if (endedAt != null && !endedAt.isAfter(startedAt)) {
                    throw ApiException.badRequest("SLEEP_WINDOW_INVALID",
                            "Sleep must end after it started");
                }
                log.setStartedAt(startedAt);
                log.setEndedAt(endedAt);
                log.setLoggedAt(startedAt);
            }
            case DIAPER -> {
                rejectForeign(detail.method() != null || detail.amountMl() != null
                        || detail.durationMin() != null || detail.startedAt() != null
                        || detail.endedAt() != null, CareType.DIAPER);
                log.setCondition(detail.condition());
                log.setWet(detail.wet());
            }
        }
    }

    private void rejectForeign(boolean foreignPresent, CareType type) {
        if (foreignPresent) {
            throw ApiException.badRequest("CARE_DETAIL_TYPE_MISMATCH",
                    "Detail fields do not belong to a " + type + " entry");
        }
    }

    private void requireNotFuture(Instant loggedAt) {
        // Small grace window absorbs client/server clock skew.
        if (loggedAt.isAfter(Instant.now().plusSeconds(120))) {
            throw ApiException.badRequest("LOGGED_AT_IN_FUTURE", "Log time cannot be in the future");
        }
    }
}
