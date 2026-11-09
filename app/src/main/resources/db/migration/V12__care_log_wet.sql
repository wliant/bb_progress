-- Whether a nappy was wet (spec 10).
--
-- The clinical reference people actually use is wet nappies per day, which the
-- `condition` column from V10 does not answer: a change can be wet and clean, or
-- dirty and dry. This is a separate fact, so it gets its own column.
--
-- Deliberately NOT backfilled. The pre-V10 notes ("No poo", "A little poo") say
-- nothing reliable about wetness, and guessing would poison the only data the
-- reference reads. Historical rows stay null and are reported as not comparable.

ALTER TABLE care_log ADD COLUMN wet BOOLEAN;

ALTER TABLE care_log
    ADD CONSTRAINT care_log_wet_detail_only
        CHECK (type = 'DIAPER' OR wet IS NULL);
