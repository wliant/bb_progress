package com.bb.progress.carelog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareLogRepository extends JpaRepository<CareLog, UUID> {

    List<CareLog> findAllByLoggedAtGreaterThanEqualAndLoggedAtLessThanOrderByLoggedAtDesc(
            Instant fromInclusive, Instant toExclusive);

    List<CareLog> findAllByTypeAndLoggedAtGreaterThanEqualAndLoggedAtLessThanOrderByLoggedAtDesc(
            CareType type, Instant fromInclusive, Instant toExclusive);
}
