package com.bb.progress.growth;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrowthRecordRepository extends JpaRepository<GrowthRecord, UUID> {

    List<GrowthRecord> findAllByOrderByMeasuredOnAsc();

    Optional<GrowthRecord> findByMeasuredOn(LocalDate measuredOn);

    Optional<GrowthRecord> findByBirthTrue();

    /** The birth record re-dates with the profile, so it is excluded from that check. */
    Optional<GrowthRecord> findFirstByBirthFalseOrderByMeasuredOnAsc();
}
