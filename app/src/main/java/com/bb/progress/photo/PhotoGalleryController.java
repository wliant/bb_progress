package com.bb.progress.photo;

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
 * A read-only view over the photos the other features already hold — there is no separate photo
 * store. Photos are added and removed from the entry they belong to; this only collects them.
 */
@RestController
public class PhotoGalleryController {

    public enum PhotoSource {
        PROFILE,
        MILESTONE,
        CARE_LOG
    }

    public record GalleryPhoto(
            String id,
            PhotoSource source,
            String url,
            String thumbnailUrl,
            /** SGT date the photo belongs to; null for the profile photo, which has no date. */
            LocalDate takenOn,
            OffsetDateTime takenAt,
            CareType careType,
            String titleEn,
            String titleZh,
            String note) {
    }

    private final BabyRepository babies;
    private final CareLogRepository careLogs;
    private final MilestoneAchievementRepository achievements;
    private final MilestoneDefinitionRepository definitions;

    public PhotoGalleryController(BabyRepository babies, CareLogRepository careLogs,
            MilestoneAchievementRepository achievements, MilestoneDefinitionRepository definitions) {
        this.babies = babies;
        this.careLogs = careLogs;
        this.achievements = achievements;
        this.definitions = definitions;
    }

    @GetMapping("/api/photos")
    @Transactional(readOnly = true)
    public List<GalleryPhoto> list() {
        List<GalleryPhoto> photos = new ArrayList<>();

        for (CareLog log : careLogs.findAllByPhotoPathIsNotNullOrderByLoggedAtDesc()) {
            OffsetDateTime loggedAt = log.getLoggedAt().atZone(Sgt.ZONE).toOffsetDateTime();
            photos.add(new GalleryPhoto(
                    "care-log:" + log.getId(),
                    PhotoSource.CARE_LOG,
                    photoUrl("/api/care-logs/" + log.getId() + "/photo", log.getPhotoPath(), false),
                    photoUrl("/api/care-logs/" + log.getId() + "/photo", log.getPhotoPath(), true),
                    loggedAt.toLocalDate(),
                    loggedAt,
                    log.getType(),
                    null, null,
                    log.getNote()));
        }

        Map<String, MilestoneDefinition> definitionsById = definitions.findAll().stream()
                .collect(Collectors.toMap(MilestoneDefinition::getId, Function.identity()));
        for (MilestoneAchievement achievement : achievements.findAllByPhotoPathIsNotNull()) {
            MilestoneDefinition definition = definitionsById.get(achievement.getMilestoneId());
            photos.add(new GalleryPhoto(
                    "milestone:" + achievement.getMilestoneId(),
                    PhotoSource.MILESTONE,
                    photoUrl(milestonePath(achievement), achievement.getPhotoPath(), false),
                    photoUrl(milestonePath(achievement), achievement.getPhotoPath(), true),
                    achievement.getAchievedOn(),
                    null,
                    null,
                    definition == null ? null : definition.getTitleEn(),
                    definition == null ? null : definition.getTitleZh(),
                    achievement.getNote()));
        }

        // Newest first; the profile photo has no date of its own so it leads the list.
        photos.sort(Comparator.comparing(GalleryPhoto::takenOn, Comparator.reverseOrder())
                .thenComparing(photo -> photo.takenAt() == null ? OffsetDateTime.MIN : photo.takenAt(),
                        Comparator.reverseOrder()));

        babies.findFirstByOrderByCreatedAtAsc()
                .filter(baby -> baby.getPhotoPath() != null)
                .map(this::profilePhoto)
                .ifPresent(photo -> photos.add(0, photo));

        return photos;
    }

    private GalleryPhoto profilePhoto(Baby baby) {
        return new GalleryPhoto(
                "profile",
                PhotoSource.PROFILE,
                photoUrl("/api/baby/photo", baby.getPhotoPath(), false),
                photoUrl("/api/baby/photo", baby.getPhotoPath(), true),
                null, null, null, null, null, null);
    }

    private static String milestonePath(MilestoneAchievement achievement) {
        return "/api/milestones/" + achievement.getMilestoneId() + "/achievement/photo";
    }

    private static String photoUrl(String path, String photoPath, boolean thumbnail) {
        String version = PhotoResponses.versionOf(photoPath);
        return thumbnail
                ? path + "?size=" + PhotoResponses.THUMBNAIL + "&v=" + version
                : path + "?v=" + version;
    }
}
