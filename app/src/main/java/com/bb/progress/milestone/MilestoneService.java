package com.bb.progress.milestone;

import com.bb.progress.baby.BabyService;
import com.bb.progress.common.ApiException;
import com.bb.progress.milestone.MilestoneDtos.AchievementRequest;
import com.bb.progress.milestone.MilestoneDtos.AchievementView;
import com.bb.progress.milestone.MilestoneDtos.AgeGroupView;
import com.bb.progress.milestone.MilestoneDtos.MilestoneView;
import com.bb.progress.photo.ImageCompressor;
import com.bb.progress.photo.PhotoStorageService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MilestoneService {

    private final MilestoneDefinitionRepository definitions;
    private final MilestoneAchievementRepository achievements;
    private final PhotoStorageService photoStorage;
    private final BabyService babyService;

    public MilestoneService(MilestoneDefinitionRepository definitions,
            MilestoneAchievementRepository achievements,
            PhotoStorageService photoStorage,
            BabyService babyService) {
        this.definitions = definitions;
        this.achievements = achievements;
        this.photoStorage = photoStorage;
        this.babyService = babyService;
    }

    @Transactional(readOnly = true)
    public List<AgeGroupView> listGroupedByAge() {
        Map<String, MilestoneAchievement> byMilestoneId = achievements.findAll().stream()
                .collect(Collectors.toMap(MilestoneAchievement::getMilestoneId, Function.identity()));
        Map<Integer, List<MilestoneView>> groups = new LinkedHashMap<>();
        for (MilestoneDefinition definition : definitions.findAllByOrderByAgeMonthsAscSortOrderAsc()) {
            MilestoneAchievement achievement = byMilestoneId.get(definition.getId());
            groups.computeIfAbsent(definition.getAgeMonths(), k -> new ArrayList<>())
                    .add(new MilestoneView(definition.getId(), definition.getCategory(),
                            definition.getTitleEn(), definition.getTitleZh(),
                            achievement == null ? null : AchievementView.from(achievement)));
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
        return AchievementView.from(requireAchievement(definitionId));
    }

    @Transactional
    public void removeAchievement(String definitionId) {
        MilestoneAchievement achievement = requireAchievement(definitionId);
        achievements.delete(achievement);
        photoStorage.deleteIfExists(achievement.getPhotoPath());
    }

    @Transactional
    public AchievementView updatePhoto(String definitionId, MultipartFile file) {
        MilestoneAchievement achievement = requireAchievement(definitionId);
        String oldPath = achievement.getPhotoPath();
        achievement.setPhotoPath(photoStorage.storeCompressed("milestones", file, ImageCompressor.ONE_MEGABYTE));
        AchievementView view = AchievementView.from(achievements.save(achievement));
        photoStorage.deleteIfExists(oldPath);
        return view;
    }

    @Transactional(readOnly = true)
    public String getPhotoPath(String definitionId) {
        String path = requireAchievement(definitionId).getPhotoPath();
        if (path == null) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "No photo for this milestone");
        }
        return path;
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
