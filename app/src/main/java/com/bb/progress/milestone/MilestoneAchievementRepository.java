package com.bb.progress.milestone;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneAchievementRepository extends JpaRepository<MilestoneAchievement, UUID> {

    Optional<MilestoneAchievement> findByMilestoneId(String milestoneId);
}
