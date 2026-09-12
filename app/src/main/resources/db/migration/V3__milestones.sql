CREATE TABLE milestone_definition (
    id TEXT PRIMARY KEY,
    age_months INT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('SOCIAL', 'LANGUAGE', 'COGNITIVE', 'MOTOR')),
    title_en TEXT NOT NULL,
    title_zh TEXT NOT NULL,
    sort_order INT NOT NULL
);

CREATE TABLE milestone_achievement (
    id UUID PRIMARY KEY,
    milestone_id TEXT NOT NULL UNIQUE REFERENCES milestone_definition (id),
    achieved_on DATE NOT NULL,
    photo_path TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
