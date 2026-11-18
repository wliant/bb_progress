package com.bb.progress.headshape;

import com.bb.progress.media.MediaDtos.MediaView;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HeadShapeDtos {

    private HeadShapeDtos() {
    }

    public record HeadShapeEntryRequest(
            @NotNull LocalDate recordedOn,
            @Size(max = 500) String note) {
    }

    public record HeadShapeEntryResponse(
            UUID id,
            LocalDate recordedOn,
            String note,
            List<MediaView> media) {

        /** Media is passed in rather than fetched here, so a list can batch its lookups. */
        public static HeadShapeEntryResponse from(HeadShapeEntry entry, List<MediaView> media) {
            return new HeadShapeEntryResponse(entry.getId(), entry.getRecordedOn(), entry.getNote(),
                    media);
        }
    }
}
