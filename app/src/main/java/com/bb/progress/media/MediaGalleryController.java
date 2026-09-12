package com.bb.progress.media;

import com.bb.progress.baby.Baby;
import com.bb.progress.baby.BabyRepository;
import com.bb.progress.carelog.CareLog;
import com.bb.progress.carelog.CareLogRepository;
import com.bb.progress.carelog.CareType;
import com.bb.progress.common.Sgt;
import com.bb.progress.milestone.MilestoneAchievement;
import com.bb.progress.milestone.MilestoneAchievementRepository;
import com.bb.progress.milestone.MilestoneDefinition;
import com.bb.progress.milestone.MilestoneDefinitionRepository;
import com.bb.progress.photo.PhotoResponses;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A read-only view over the attachments the other features already hold — there is no separate
 * media store. Media is added and removed on the entry it belongs to; this only collects it.
 */
@RestController
public class MediaGalleryController {

    public enum MediaSource {
        PROFILE,
        MILESTONE,
        CARE_LOG
    }

    public record GalleryItem(
            String id,
            MediaSource source,
            MediaKind kind,
            String url,
            /** Null for video and audio, which have no generated frame. */
            String thumbnailUrl,
            /** SGT date the item belongs to; null for the profile photo, which has no date. */
            LocalDate takenOn,
            OffsetDateTime takenAt,
            CareType careType,
            String titleEn,
            String titleZh,
            String note) {
    }

    private final BabyRepository babies;
    private final CareLogRepository careLogs;
    private final MediaRepository media;
    private final MilestoneAchievementRepository achievements;
    private final MilestoneDefinitionRepository definitions;

    public MediaGalleryController(BabyRepository babies, CareLogRepository careLogs,
            MediaRepository media, MilestoneAchievementRepository achievements,
            MilestoneDefinitionRepository definitions) {
        this.babies = babies;
        this.careLogs = careLogs;
        this.media = media;
        this.achievements = achievements;
        this.definitions = definitions;
    }

    @GetMapping("/api/media")
    @Transactional(readOnly = true)
    public List<GalleryItem> list() {
        List<GalleryItem> items = new ArrayList<>();
        List<Media> all = media.findAll();

        Map<java.util.UUID, CareLog> logsById = careLogs.findAll().stream()
                .collect(Collectors.toMap(CareLog::getId, Function.identity()));
        Map<String, MilestoneAchievement> achievementsById = achievements.findAll().stream()
                .collect(Collectors.toMap(MilestoneAchievement::getMilestoneId, Function.identity()));
        Map<String, MilestoneDefinition> definitionsById = definitions.findAll().stream()
                .collect(Collectors.toMap(MilestoneDefinition::getId, Function.identity()));

        for (Media item : all) {
            String url = "/api/media/" + item.getId() + "/content";
            String thumbnail = item.getKind() == MediaKind.PHOTO ? url + "?size=thumb" : null;

            if (item.getCareLogId() != null) {
                CareLog log = logsById.get(item.getCareLogId());
                if (log == null) {
                    continue;
                }
                OffsetDateTime loggedAt = log.getLoggedAt().atZone(Sgt.ZONE).toOffsetDateTime();
                items.add(new GalleryItem("media:" + item.getId(), MediaSource.CARE_LOG,
                        item.getKind(), url, thumbnail, loggedAt.toLocalDate(), loggedAt,
                        log.getType(), null, null, log.getNote()));
            } else {
                MilestoneAchievement achievement = achievementsById.get(item.getMilestoneId());
                MilestoneDefinition definition = definitionsById.get(item.getMilestoneId());
                if (achievement == null) {
                    continue;
                }
                items.add(new GalleryItem("media:" + item.getId(), MediaSource.MILESTONE,
                        item.getKind(), url, thumbnail, achievement.getAchievedOn(), null, null,
                        definition == null ? null : definition.getTitleEn(),
                        definition == null ? null : definition.getTitleZh(),
                        achievement.getNote()));
            }
        }

        // Newest first; the profile photo has no date of its own so it leads the list.
        items.sort(Comparator.comparing(GalleryItem::takenOn, Comparator.reverseOrder())
                .thenComparing(item -> item.takenAt() == null ? OffsetDateTime.MIN : item.takenAt(),
                        Comparator.reverseOrder()));

        babies.findFirstByOrderByCreatedAtAsc()
                .filter(baby -> baby.getPhotoPath() != null)
                .map(MediaGalleryController::profilePhoto)
                .ifPresent(item -> items.add(0, item));

        return items;
    }

    private static GalleryItem profilePhoto(Baby baby) {
        String version = PhotoResponses.versionOf(baby.getPhotoPath());
        return new GalleryItem("profile", MediaSource.PROFILE, MediaKind.PHOTO,
                "/api/baby/photo?v=" + version,
                "/api/baby/photo?size=thumb&v=" + version,
                null, null, null, null, null, null);
    }
}
