package com.bb.progress.milestone;

import com.bb.progress.baby.BabyService;
import com.bb.progress.common.ApiException;
import com.bb.progress.milestone.MilestoneDtos.AchievementRequest;
import com.bb.progress.milestone.MilestoneDtos.AchievementView;
import com.bb.progress.milestone.MilestoneDtos.AgeGroupView;
import com.bb.progress.milestone.MilestoneDtos.MilestoneView;
import com.bb.progress.media.Media;
import com.bb.progress.media.MediaService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MilestoneService {

    private final MilestoneDefinitionRepository definitions;
    private final MilestoneAchievementRepository achievements;
    private final MediaService mediaService;
    private final BabyService babyService;

    public MilestoneService(MilestoneDefinitionRepository definitions,
            MilestoneAchievementRepository achievements,
            MediaService mediaService,
            BabyService babyService) {
        this.definitions = definitions;
        this.achievements = achievements;
        this.mediaService = mediaService;
        this.babyService = babyService;
    }

    @Transactional(readOnly = true)
    public List<AgeGroupView> listGroupedByAge() {
        Map<String, MilestoneAchievement> byMilestoneId = achievements.findAll().stream()
                .collect(Collectors.toMap(MilestoneAchievement::getMilestoneId, Function.identity()));
        // One query for every achievement's attachments rather than one per milestone.
        Map<String, List<Media>> mediaByMilestone = mediaService
                .forMilestones(List.copyOf(byMilestoneId.keySet())).stream()
                .collect(Collectors.groupingBy(Media::getMilestoneId));
        Map<Integer, List<MilestoneView>> groups = new LinkedHashMap<>();
        for (MilestoneDefinition definition : definitions.findAllByOrderByAgeMonthsAscSortOrderAsc()) {
            MilestoneAchievement achievement = byMilestoneId.get(definition.getId());
            groups.computeIfAbsent(definition.getAgeMonths(), k -> new ArrayList<>())
                    .add(new MilestoneView(definition.getId(), definition.getCategory(),
                            definition.getTitleEn(), definition.getTitleZh(),
                            achievement == null ? null : AchievementView.from(achievement,
                                    MediaService.views(mediaByMilestone
                                            .getOrDefault(definition.getId(), List.of())))));
        }
        return groups.entrySet().stream()
                .map(e -> new AgeGroupView(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional
    public AchievementView setAchievement(String definitionId, AchievementRequest request) {
        requireDefinition(definitionId);
        if (request.achievedOn().isBefore(babyService.get().getDateOfBirth())) {
            throw ApiException.badRequest("ACHIEVED_BEFORE_BIRTH", "Achievement date is before the date of birth");
        }
        achievements.upsert(UUID.randomUUID(), definitionId, request.achievedOn(), request.note());
        return view(requireAchievement(definitionId));
    }

    @Transactional
    public void removeAchievement(String definitionId) {
        MilestoneAchievement achievement = requireAchievement(definitionId);
        // The rows cascade with the achievement, but the stored objects have to go explicitly.
        mediaService.deleteObjectsFor(mediaService.forMilestone(definitionId));
        achievements.delete(achievement);
    }

    private AchievementView view(MilestoneAchievement achievement) {
        return AchievementView.from(achievement,
                MediaService.views(mediaService.forMilestone(achievement.getMilestoneId())));
    }



    private void requireDefinition(String definitionId) {
        if (!definitions.existsById(definitionId)) {
            throw ApiException.notFound("MILESTONE_NOT_FOUND", "Milestone not found");
        }
    }

    private MilestoneAchievement requireAchievement(String definitionId) {
        requireDefinition(definitionId);
        return achievements.findByMilestoneId(definitionId)
                .orElseThrow(() -> ApiException.notFound("ACHIEVEMENT_NOT_FOUND", "Milestone not achieved yet"));
    }
}
