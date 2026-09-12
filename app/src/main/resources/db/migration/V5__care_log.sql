CREATE TABLE care_log (
    id UUID PRIMARY KEY,
    type TEXT NOT NULL CHECK (type IN ('FEEDING', 'SLEEP', 'DIAPER')),
    logged_at TIMESTAMPTZ NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_care_log_logged_at ON care_log (logged_at);
