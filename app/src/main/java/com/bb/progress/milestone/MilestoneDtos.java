package com.bb.progress.milestone;

import com.bb.progress.photo.PhotoResponses;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class MilestoneDtos {

    private MilestoneDtos() {
    }

    public record AchievementRequest(
            @NotNull @PastOrPresent LocalDate achievedOn,
            @Size(max = 500) String note) {
    }

    public record AchievementView(LocalDate achievedOn, String note, boolean hasPhoto, String photoVersion) {

        public static AchievementView from(MilestoneAchievement achievement) {
            return new AchievementView(achievement.getAchievedOn(), achievement.getNote(),
                    achievement.getPhotoPath() != null, PhotoResponses.versionOf(achievement.getPhotoPath()));
        }
    }

    public record MilestoneView(
            String id,
            MilestoneCategory category,
            String titleEn,
            String titleZh,
            AchievementView achievement) {
    }

    public record AgeGroupView(int ageMonths, List<MilestoneView> milestones) {
    }
}
