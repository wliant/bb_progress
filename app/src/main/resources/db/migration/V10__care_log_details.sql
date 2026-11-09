-- Structured detail per care type (spec 08). The free-text `note` stays as it is:
-- it carries prose no schema should swallow, and on rows backfilled by V11 it is
-- the audit trail for every parsed value.
--
-- Additive and nullable throughout. The table is live, so no NOT NULL is added
-- here; a quick-logged entry legitimately has no detail yet.

ALTER TABLE care_log
    ADD COLUMN feed_method  TEXT,
    ADD COLUMN amount_ml    INTEGER,
    ADD COLUMN duration_min INTEGER,
    ADD COLUMN started_at   TIMESTAMPTZ,
    ADD COLUMN ended_at     TIMESTAMPTZ,
    ADD COLUMN condition    TEXT;

-- Value domains.
ALTER TABLE care_log
    ADD CONSTRAINT care_log_feed_method_values
        CHECK (feed_method IS NULL OR feed_method IN ('BREAST', 'BOTTLE', 'BOTH')),
    ADD CONSTRAINT care_log_condition_values
        CHECK (condition IS NULL OR condition IN ('CLEAN', 'SLIGHTLY_DIRTY', 'DIRTY', 'VERY_DIRTY')),
    ADD CONSTRAINT care_log_amount_ml_range
        CHECK (amount_ml IS NULL OR amount_ml BETWEEN 1 AND 500),
    ADD CONSTRAINT care_log_duration_min_range
        CHECK (duration_min IS NULL OR duration_min BETWEEN 1 AND 180);

-- A sleep window is open (end unset) or closed and forward-going. Duration is always
-- derived from these two, never stored, so an edit cannot leave the two disagreeing.
ALTER TABLE care_log
    ADD CONSTRAINT care_log_sleep_window
        CHECK (ended_at IS NULL OR (started_at IS NOT NULL AND ended_at > started_at));

-- Each detail column belongs to exactly one care type, enforced here rather than only
-- in the service, so no path can write a diaper condition onto a feed.
ALTER TABLE care_log
    ADD CONSTRAINT care_log_feeding_detail_only
        CHECK (type = 'FEEDING'
               OR (feed_method IS NULL AND amount_ml IS NULL AND duration_min IS NULL)),
    ADD CONSTRAINT care_log_sleep_detail_only
        CHECK (type = 'SLEEP' OR (started_at IS NULL AND ended_at IS NULL)),
    ADD CONSTRAINT care_log_diaper_detail_only
        CHECK (type = 'DIAPER' OR condition IS NULL);

-- The aggregate queries of spec 09 group by type over a date window; the existing
-- idx_care_log_logged_at cannot serve that without a filter on type.
CREATE INDEX idx_care_log_type_logged_at ON care_log (type, logged_at);
