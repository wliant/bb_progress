package com.bb.progress.baby;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BabyRepository extends JpaRepository<Baby, UUID> {

    Optional<Baby> findFirstByOrderByCreatedAtAsc();
}
