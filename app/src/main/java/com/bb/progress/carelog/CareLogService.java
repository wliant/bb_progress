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
        CareLog saved = repository.save(new CareLog(request.type(), loggedAt, request.note()));
        return CareLogResponse.from(saved, List.of());
    }

    @Transactional
    public CareLogResponse update(UUID id, CareLogUpdateRequest request) {
        CareLog log = require(id);
        Instant loggedAt = request.loggedAt().toInstant();
        requireNotFuture(loggedAt);
        log.setLoggedAt(loggedAt);
        log.setNote(request.note());
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

    private void requireNotFuture(Instant loggedAt) {
        // Small grace window absorbs client/server clock skew.
        if (loggedAt.isAfter(Instant.now().plusSeconds(120))) {
            throw ApiException.badRequest("LOGGED_AT_IN_FUTURE", "Log time cannot be in the future");
        }
    }
}
