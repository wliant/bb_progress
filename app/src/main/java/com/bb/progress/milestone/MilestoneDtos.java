package com.bb.progress.milestone;

import com.bb.progress.media.MediaDtos.MediaView;
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

    public record AchievementView(LocalDate achievedOn, String note, List<MediaView> media) {

        public static AchievementView from(MilestoneAchievement achievement, List<MediaView> media) {
            return new AchievementView(achievement.getAchievedOn(), achievement.getNote(), media);
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
