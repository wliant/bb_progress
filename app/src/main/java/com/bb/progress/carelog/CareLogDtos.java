package com.bb.progress.carelog;

import com.bb.progress.common.Sgt;
import com.bb.progress.media.MediaDtos.MediaView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CareLogDtos {

    private CareLogDtos() {
    }

    /**
     * The detail fields every write shares. Each belongs to exactly one care type and is
     * rejected when sent for another ({@code CARE_DETAIL_TYPE_MISMATCH}), matching the
     * CHECK constraints in V10 so neither layer can be the only thing holding the rule.
     */
    public interface CareDetail {

        FeedMethod method();

        Integer amountMl();

        Integer durationMin();

        OffsetDateTime startedAt();

        OffsetDateTime endedAt();

        DiaperCondition condition();

        Boolean wet();
    }

    public record CareLogCreateRequest(
            @NotNull CareType type,
            OffsetDateTime loggedAt,
            @Size(max = 500) String note,
            FeedMethod method,
            @Min(1) @Max(500) Integer amountMl,
            @Min(1) @Max(180) Integer durationMin,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            DiaperCondition condition,
            Boolean wet) implements CareDetail {
    }

    public record CareLogUpdateRequest(
            @NotNull OffsetDateTime loggedAt,
            @Size(max = 500) String note,
            FeedMethod method,
            @Min(1) @Max(500) Integer amountMl,
            @Min(1) @Max(180) Integer durationMin,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            DiaperCondition condition,
            Boolean wet) implements CareDetail {
    }

    public record CareLogResponse(
            UUID id,
            CareType type,
            OffsetDateTime loggedAt,
            String note,
            FeedMethod method,
            Integer amountMl,
            Integer durationMin,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            /** Derived from the window, so the client never computes it differently. */
            Long sleepMinutes,
            DiaperCondition condition,
            Boolean wet,
            List<MediaView> media) {

        public static CareLogResponse from(CareLog log, List<MediaView> media) {
            return new CareLogResponse(
                    log.getId(),
                    log.getType(),
                    sgt(log.getLoggedAt()),
                    log.getNote(),
                    log.getFeedMethod(),
                    log.getAmountMl(),
                    log.getDurationMin(),
                    sgt(log.getStartedAt()),
                    sgt(log.getEndedAt()),
                    log.getSleepMinutes(),
                    log.getCondition(),
                    log.getWet(),
                    media);
        }

        private static OffsetDateTime sgt(java.time.Instant instant) {
            return instant == null ? null : instant.atZone(Sgt.ZONE).toOffsetDateTime();
        }
    }
}
