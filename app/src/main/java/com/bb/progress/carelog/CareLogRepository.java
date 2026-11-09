package com.bb.progress.carelog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareLogRepository extends JpaRepository<CareLog, UUID> {

    List<CareLog> findAllByLoggedAtGreaterThanEqualAndLoggedAtLessThanOrderByLoggedAtDesc(
            Instant fromInclusive, Instant toExclusive);

    List<CareLog> findAllByTypeAndLoggedAtGreaterThanEqualAndLoggedAtLessThanOrderByLoggedAtDesc(
            CareType type, Instant fromInclusive, Instant toExclusive);

    /**
     * Every per-day figure spec 09 needs, in one grouped pass over the window rather than
     * loading entries into the application. Days are SGT calendar days, which is why the
     * grouping converts the timestamp rather than truncating it.
     *
     * <p>{@code feedsWithAmount} counts non-null amounts, so it reports coverage: a day
     * where it is below {@code feedsTotal} has an incomplete volume total. A sleep is
     * attributed to the day it began, even when it ends after midnight.
     */
    @Query(value = """
            SELECT (logged_at AT TIME ZONE 'Asia/Singapore')::date AS "day",
                   COALESCE(SUM(amount_ml) FILTER (WHERE type = 'FEEDING'), 0)::int
                       AS "amountMl",
                   COUNT(*) FILTER (WHERE type = 'FEEDING')::int
                       AS "feedsTotal",
                   COUNT(amount_ml) FILTER (WHERE type = 'FEEDING')::int
                       AS "feedsWithAmount",
                   COALESCE(SUM(duration_min) FILTER (WHERE type = 'FEEDING'), 0)::int
                       AS "breastMinutes",
                   COALESCE(SUM(EXTRACT(EPOCH FROM (ended_at - started_at)) / 60)
                       FILTER (WHERE type = 'SLEEP' AND ended_at IS NOT NULL), 0)::bigint
                       AS "sleepMinutes",
                   COUNT(*) FILTER (WHERE type = 'SLEEP')::int
                       AS "sleepSessions",
                   COUNT(*) FILTER (WHERE type = 'SLEEP' AND ended_at IS NULL)::int
                       AS "openSleeps",
                   COUNT(*) FILTER (WHERE type = 'DIAPER')::int
                       AS "diaperTotal",
                   COUNT(*) FILTER (WHERE type = 'DIAPER' AND condition = 'CLEAN')::int
                       AS "diaperClean",
                   COUNT(*) FILTER (WHERE type = 'DIAPER' AND condition = 'SLIGHTLY_DIRTY')::int
                       AS "diaperSlightlyDirty",
                   COUNT(*) FILTER (WHERE type = 'DIAPER' AND condition = 'DIRTY')::int
                       AS "diaperDirty",
                   COUNT(*) FILTER (WHERE type = 'DIAPER' AND condition = 'VERY_DIRTY')::int
                       AS "diaperVeryDirty",
                   COUNT(*) FILTER (WHERE type = 'DIAPER' AND condition IS NULL)::int
                       AS "diaperUnknown"
            FROM care_log
            WHERE logged_at >= :fromInclusive AND logged_at < :toExclusive
            GROUP BY "day"
            ORDER BY "day"
            """, nativeQuery = true)
    List<CareAggregateRow> aggregateByDay(@Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);
}
