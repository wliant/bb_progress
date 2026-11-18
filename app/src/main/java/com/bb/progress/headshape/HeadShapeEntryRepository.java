package com.bb.progress.headshape;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeadShapeEntryRepository extends JpaRepository<HeadShapeEntry, UUID> {

    List<HeadShapeEntry> findAllByOrderByRecordedOnDesc();

    Optional<HeadShapeEntry> findByRecordedOn(LocalDate recordedOn);
}
