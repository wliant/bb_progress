package com.bb.progress.milestone;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneDefinitionRepository extends JpaRepository<MilestoneDefinition, String> {

    List<MilestoneDefinition> findAllByOrderByAgeMonthsAscSortOrderAsc();
}
