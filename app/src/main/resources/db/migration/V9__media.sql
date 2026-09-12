-- Care logs and milestones move from a single photo to any number of attachments.
CREATE TABLE media (
    id UUID PRIMARY KEY,
    care_log_id UUID REFERENCES care_log (id) ON DELETE CASCADE,
    milestone_id TEXT REFERENCES milestone_achievement (milestone_id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('PHOTO', 'VIDEO', 'AUDIO')),
    object_key TEXT NOT NULL,
    content_type TEXT NOT NULL,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    -- An attachment belongs to exactly one owner.
    CONSTRAINT media_one_owner CHECK (
        (care_log_id IS NOT NULL)::int + (milestone_id IS NOT NULL)::int = 1
    )
);

CREATE INDEX idx_media_care_log ON media (care_log_id);
CREATE INDEX idx_media_milestone ON media (milestone_id);

-- Carry the existing single photos over so nothing already uploaded is lost.
INSERT INTO media (id, care_log_id, milestone_id, kind, object_key, content_type, sort_order, created_at)
SELECT gen_random_uuid(), id, NULL, 'PHOTO', photo_path,
       CASE WHEN photo_path LIKE '%.png' THEN 'image/png'
            WHEN photo_path LIKE '%.webp' THEN 'image/webp'
            ELSE 'image/jpeg' END,
       0, created_at
FROM care_log WHERE photo_path IS NOT NULL;

INSERT INTO media (id, care_log_id, milestone_id, kind, object_key, content_type, sort_order, created_at)
SELECT gen_random_uuid(), NULL, milestone_id, 'PHOTO', photo_path,
       CASE WHEN photo_path LIKE '%.png' THEN 'image/png'
            WHEN photo_path LIKE '%.webp' THEN 'image/webp'
            ELSE 'image/jpeg' END,
       0, created_at
FROM milestone_achievement WHERE photo_path IS NOT NULL;

ALTER TABLE care_log DROP COLUMN photo_path;
ALTER TABLE milestone_achievement DROP COLUMN photo_path;
