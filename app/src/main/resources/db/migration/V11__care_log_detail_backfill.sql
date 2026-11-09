-- Backfill the V10 columns from the free-text notes written before the app had
-- fields for them (spec 08). Separate from V10 so the schema change stands even if
-- the parse has to be corrected by a later forward migration.
--
-- `note` is deliberately NOT cleared. Every value below remains checkable against the
-- text it came from, which is what makes a wrong parse correctable later.
--
-- The parse is expressed as SQL regexes rather than a fixed list of note texts, so
-- entries logged between the dump and this migration running are covered too.

-- ---------------------------------------------------------------------------
-- FEEDING: volume in ml, and minutes at the breast summed across sides.
-- ---------------------------------------------------------------------------
WITH ml AS (
    -- Every "<n> ml" in the note, summed: "30ml breast milk 25ml formula" is one 55 ml feed.
    SELECT c.id, SUM(x.m[1]::numeric) AS total
    FROM care_log c
    CROSS JOIN LATERAL regexp_matches(c.note, '(\d+(?:\.\d+)?)\s*ml\y', 'gi') AS x(m)
    WHERE c.type = 'FEEDING'
    GROUP BY c.id
),
bare AS (
    -- A note that is nothing but a number was an ml figure with the unit left off.
    SELECT id, (btrim(note))::numeric AS total
    FROM care_log
    WHERE type = 'FEEDING' AND note ~ '^\s*\d+(\.\d+)?\s*$'
),
each_side AS (
    -- "15min each side" is 30 minutes, and must not also be read as a single side.
    SELECT c.id, (x.m[1]::int) * 2 AS total
    FROM care_log c
    CROSS JOIN LATERAL
        regexp_matches(c.note, '(\d+)\s*(?:min|minute)s?\s*each\s+side', 'i') AS x(m)
    WHERE c.type = 'FEEDING'
),
sides AS (
    -- One alternation covering both orders the notes use -- "Left 15, right 7" and
    -- "15min left, 10min right" -- scanned left to right without overlap, so a side
    -- is never counted twice. Minutes are optional: "Left 15, right 7" has no unit.
    SELECT c.id, SUM(COALESCE(x.m[2], x.m[3])::int) AS total
    FROM care_log c
    CROSS JOIN LATERAL regexp_matches(
        c.note,
        '(?:(left|right)\s*(?:side)?\s*(\d+)\s*(?:min|minute)?s?\y)'
        '|(?:(\d+)\s*(?:min|minute)s?\s*(left|right))', 'gi') AS x(m)
    WHERE c.type = 'FEEDING'
    GROUP BY c.id
),
parsed AS (
    SELECT c.id,
           ROUND(COALESCE(ml.total, bare.total))::int        AS amount_ml,
           COALESCE(each_side.total, sides.total)::int       AS duration_min
    FROM care_log c
    LEFT JOIN ml        ON ml.id        = c.id
    LEFT JOIN bare      ON bare.id      = c.id
    LEFT JOIN each_side ON each_side.id = c.id
    LEFT JOIN sides     ON sides.id     = c.id
    WHERE c.type = 'FEEDING'
)
UPDATE care_log c
SET amount_ml    = p.amount_ml,
    duration_min = p.duration_min,
    feed_method  = CASE
                       WHEN p.amount_ml IS NOT NULL AND p.duration_min IS NOT NULL THEN 'BOTH'
                       WHEN p.duration_min IS NOT NULL THEN 'BREAST'
                       WHEN p.amount_ml IS NOT NULL THEN 'BOTTLE'
                   END
FROM parsed p
WHERE c.id = p.id
  AND (p.amount_ml IS NOT NULL OR p.duration_min IS NOT NULL);

-- ---------------------------------------------------------------------------
-- FEEDING: the notes a mechanical read gets wrong, corrected by their exact text.
-- Each is a single live row, checked by hand against the original note.
-- ---------------------------------------------------------------------------

-- "110ml out of 130ml" is 110 ml taken from a 130 ml bottle, not 240 ml.
UPDATE care_log SET amount_ml = 110, duration_min = NULL, feed_method = 'BOTTLE'
WHERE type = 'FEEDING' AND btrim(note) = '110ml out of 130ml';

-- Formula was given alongside the breast but its volume was never written down, so the
-- feed is mixed with an unknown amount. Recording it as BOTTLE/BREAST alone would claim
-- more than the note says; a NULL amount keeps it out of the measured totals (spec 09).
UPDATE care_log SET feed_method = 'BOTH', amount_ml = NULL
WHERE type = 'FEEDING' AND btrim(note) = '15min left, 10min right, formula';

UPDATE care_log SET feed_method = 'BOTH', amount_ml = 20, duration_min = NULL
WHERE type = 'FEEDING'
  AND btrim(note) = 'Latching but not very efficient. Give formula 20ml';

-- ---------------------------------------------------------------------------
-- SLEEP: the single timestamp was the moment the baby went down. No end was ever
-- recorded, so every historical sleep migrates as an open window.
-- ---------------------------------------------------------------------------
UPDATE care_log SET started_at = logged_at
WHERE type = 'SLEEP' AND started_at IS NULL;

-- ---------------------------------------------------------------------------
-- DIAPER: the prose the notes actually use. Ordered most specific first, so
-- "a lot of poo" is not swallowed by the bare "poo" rule.
-- ---------------------------------------------------------------------------
UPDATE care_log
SET condition = CASE
        WHEN note ~* 'a lot of poo' OR btrim(note) ~* '^alot$' THEN 'VERY_DIRTY'
        WHEN note ~* 'a little poo'                            THEN 'SLIGHTLY_DIRTY'
        WHEN note ~* 'no poo|pee only|nothing much'            THEN 'CLEAN'
        WHEN note ~* 'poo'                                     THEN 'DIRTY'
    END
WHERE type = 'DIAPER' AND note IS NOT NULL AND btrim(note) <> '';
