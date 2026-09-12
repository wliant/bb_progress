ALTER TABLE baby ADD COLUMN time_of_birth TIME;

-- Birth measurements live in growth_record so they plot as the first point on the
-- growth chart; the flag marks the one record the profile owns and re-dates.
ALTER TABLE growth_record ADD COLUMN is_birth BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX idx_growth_record_single_birth
    ON growth_record ((TRUE)) WHERE is_birth;
