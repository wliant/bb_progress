-- Size at birth only means something relative to how long the pregnancy ran.
ALTER TABLE baby ADD COLUMN gestational_age_days INT
    CHECK (gestational_age_days IS NULL OR gestational_age_days BETWEEN 154 AND 315);
