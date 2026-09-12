CREATE TABLE growth_record (
    id UUID PRIMARY KEY,
    measured_on DATE NOT NULL UNIQUE,
    weight_kg NUMERIC(5, 2),
    height_cm NUMERIC(5, 1),
    head_circumference_cm NUMERIC(4, 1),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT at_least_one_measurement CHECK (
        weight_kg IS NOT NULL OR height_cm IS NOT NULL OR head_circumference_cm IS NOT NULL
    )
);
