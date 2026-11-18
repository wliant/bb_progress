-- Head shape tracking: one entry per SGT day, many photos on each.
CREATE TABLE head_shape_entry (
    id UUID PRIMARY KEY,
    recorded_on DATE NOT NULL UNIQUE,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_head_shape_entry_recorded_on ON head_shape_entry (recorded_on DESC);

-- Attachments reuse the media table rather than a second photo store.
ALTER TABLE media ADD COLUMN head_shape_entry_id UUID
    REFERENCES head_shape_entry (id) ON DELETE CASCADE;

CREATE INDEX idx_media_head_shape ON media (head_shape_entry_id);

-- The owner check was a two-way sum; widen it to three. Every existing row still satisfies it,
-- because the new column is null everywhere.
ALTER TABLE media DROP CONSTRAINT media_one_owner;
ALTER TABLE media ADD CONSTRAINT media_one_owner CHECK (
    (care_log_id IS NOT NULL)::int
  + (milestone_id IS NOT NULL)::int
  + (head_shape_entry_id IS NOT NULL)::int = 1
);
