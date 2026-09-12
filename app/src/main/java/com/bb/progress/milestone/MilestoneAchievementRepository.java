package com.bb.progress.milestone;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MilestoneAchievementRepository extends JpaRepository<MilestoneAchievement, UUID> {

    Optional<MilestoneAchievement> findByMilestoneId(String milestoneId);

    Optional<MilestoneAchievement> findFirstByOrderByAchievedOnAsc();

    List<MilestoneAchievement> findAllByPhotoPathIsNotNull();

    /**
     * Race-safe check-off: two taps on "mark achieved" both settle on the same end state
     * instead of one losing to the unique constraint. Leaves photo_path untouched.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO milestone_achievement (id, milestone_id, achieved_on, note, created_at, updated_at)
            VALUES (CAST(:id AS uuid), :milestoneId, CAST(:achievedOn AS date), CAST(:note AS text), now(), now())
            ON CONFLICT (milestone_id) DO UPDATE
            SET achieved_on = EXCLUDED.achieved_on,
                note = EXCLUDED.note,
                updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("id") UUID id,
            @Param("milestoneId") String milestoneId,
            @Param("achievedOn") LocalDate achievedOn,
            @Param("note") String note);
}
