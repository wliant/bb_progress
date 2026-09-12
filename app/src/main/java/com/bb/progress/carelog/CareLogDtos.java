package com.bb.progress.carelog;

import com.bb.progress.common.Sgt;
import com.bb.progress.media.MediaDtos.MediaView;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class CareLogDtos {

    private CareLogDtos() {
    }

    public record CareLogCreateRequest(
            @NotNull CareType type,
            OffsetDateTime loggedAt,
            @Size(max = 500) String note) {
    }

    public record CareLogUpdateRequest(
            @NotNull OffsetDateTime loggedAt,
            @Size(max = 500) String note) {
    }

    public record CareLogResponse(
            UUID id,
            CareType type,
            OffsetDateTime loggedAt,
            String note,
            List<MediaView> media) {

        public static CareLogResponse from(CareLog log, List<MediaView> media) {
            return new CareLogResponse(log.getId(), log.getType(),
                    log.getLoggedAt().atZone(Sgt.ZONE).toOffsetDateTime(), log.getNote(), media);
        }
    }
}
