package com.bb.progress.media;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    List<Media> findAllByCareLogIdOrderBySortOrderAsc(UUID careLogId);

    List<Media> findAllByMilestoneIdOrderBySortOrderAsc(String milestoneId);

    List<Media> findAllByCareLogIdInOrderBySortOrderAsc(List<UUID> careLogIds);

    List<Media> findAllByMilestoneIdInOrderBySortOrderAsc(List<String> milestoneIds);
}
