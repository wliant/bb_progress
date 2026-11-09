# Spec 08 — Structured Care Details

## Overview
Care log entries currently carry their detail as a free-text `note`. That text is where all the
real information lives — 556 of 560 production feeding entries are a volume (`80ml`) or a breast
duration (`Left 15, right 7`) written by hand — which means none of it can be totalled, charted or
compared. This spec replaces the free text with typed fields per care type, and migrates the
existing 607 live entries into them.

The `note` field stays. It carries prose that no schema should swallow (`Baby fell asleep`,
`first time of bottle feeding`), and on migrated rows it is **left verbatim** so every parsed value
remains auditable against the text it came from.

## Requirements

### Feeding
- `method` — `BREAST` | `BOTTLE` | `BOTH`, required on new entries.
- `amountMl` — integer ml, optional, 1–500 when present.
- `durationMin` — integer minutes, optional, 1–180 when present.
- Both quantities are nullable and independent, so a feed may record an amount, a duration, or both:
  - `BOTTLE` — `amountMl` expected, `durationMin` not shown.
  - `BREAST` — both fields shown; the mother expresses or weighs so an ml figure is entered
    alongside the minutes at the breast.
  - `BOTH` — both shown, `amountMl` meaning the topped-up bottle volume.
- A feeding entry with neither quantity is allowed (a one-tap quick log before the detail is known).
- **Historical breast feeds have no ml figure and must not be given one.** 137 live entries recorded
  only minutes; they migrate with `durationMin` set and `amountMl` null. Aggregates must therefore
  distinguish "no amount recorded" from zero — see spec 09.

### Sleep
- `startedAt` — timestamp, required; `endedAt` — timestamp, optional, must be after `startedAt`.
- Duration is **derived**, never stored, so an edited start or end cannot disagree with it.
- `loggedAt` remains the entry's position in the day's list and equals `startedAt` for sleep rows.
- An open sleep (no `endedAt`) is a nap in progress: the UI offers to end it, and the day view
  renders it as ongoing rather than as zero minutes.

### Diaper
- `condition` — `CLEAN` | `SLIGHTLY_DIRTY` | `DIRTY` | `VERY_DIRTY`, optional (a quick-logged change
  may not have been inspected).
- Rendered as four buttons in the entry dialog, in that order, both languages.

## Schema — V10

Additive and nullable throughout, per the live-database rules in CLAUDE.md:

```
ALTER TABLE care_log
  ADD COLUMN feed_method   TEXT,     -- BREAST | BOTTLE | BOTH
  ADD COLUMN amount_ml     INTEGER,
  ADD COLUMN duration_min  INTEGER,
  ADD COLUMN started_at    TIMESTAMPTZ,
  ADD COLUMN ended_at      TIMESTAMPTZ,
  ADD COLUMN condition     TEXT;     -- CLEAN | SLIGHTLY_DIRTY | DIRTY | VERY_DIRTY
```

With CHECK constraints that each column is only populated for its own `type`, that `feed_method`
and `condition` hold known values, that `ended_at > started_at`, and that the numeric ranges hold.
No `NOT NULL` is added to an existing table. `note` is untouched.

An index on `(type, logged_at)` serves the aggregate queries of spec 09.

## Data migration — V11

A separate migration from the schema change, so the column addition can succeed even if the
backfill needs revising. It parses `note` into the new columns and **does not clear `note`**.

The parse is written as SQL regexes rather than a fixed list of note texts, so entries logged
between the backup and the migration running are covered too. Mechanically (case-insensitive, on
560 feeding / 39 diaper / 8 sleep live rows):

- **Volume** — `(\d+)\s*ml` summed; a note that is a bare number (`100`) is read as ml.
- **Breast minutes** — `(left|right)\s*(side)?\s*(\d+)\s*(min|minute)s?` and the reversed
  `(\d+)\s*min\s*(left|right)`, summed across sides; `N min each side` doubles N.
- **Method** — minutes only → `BREAST`; ml only → `BOTTLE`; both → `BOTH`; neither → null.
- **Diaper** — `no poo` / `pee only` / `nothing much` → `CLEAN`; `a little poo` → `SLIGHTLY_DIRTY`;
  `poo` → `DIRTY`; `a lot of poo` / `alot` → `VERY_DIRTY`.
- **Sleep** — `started_at = logged_at` for all 8 rows, `ended_at` null. `3.5 hours` is left
  unparsed; one row is not worth the parser surface.

### Rows the parser must not guess at
A handful of live notes need a decision a regex cannot make. They are corrected by **explicit
UPDATE matched on the exact note text**, each one a single live row checked by hand:

| note | correct result |
|---|---|
| `110ml out of 130ml` | 110 ml (naive sum gives 240) |
| `15min left side 10min left side. Baby fell asleep` | 25 min (both sides say "left"; overlapping matches give 35) |
| `90ml (first time of bottle feeding)` | 90 ml |
| `Latching but not very efficient. Give formula 20ml` | 20 ml, `BOTTLE` |
| `15ml breast milk 15ml formula` | 30 ml, `BOTTLE` (sums correctly; no override needed) |
| `15min left, 60ml breast milk` | 15 min + 60 ml, `BOTH` (sums correctly) |
| `Left 13, right 16 minutes, and 35 ml of breast milk` | 29 min + 35 ml, `BOTH` (sums correctly) |
| `15 min left 5 min right, 30ml breast milk 25 ml formula` | 20 min + 55 ml, `BOTH` (sums correctly) |
| `15min left, 10min right, formula` | 25 min, `BOTH`, **amount null** |
| `Latching but not very efficient. Give formula 20ml` | 20 ml, `BOTH`, duration null |

The last two are the only real judgement calls. Both describe a feed where formula was given
*alongside* the breast but one of the two quantities was never written down. Recording them as
`BOTTLE` or `BREAST` alone would claim more than the note says; `BOTH` with the missing quantity
left null is the truthful reading, and it keeps them out of the measured totals in spec 09.

Because `note` is preserved, a wrong parse is correctable later by a forward migration reading the
same text — nothing is lost by migrating.

## API
- `POST /api/care-logs` and `PUT /api/care-logs/{id}` accept `method`, `amountMl`, `durationMin`,
  `startedAt`, `endedAt`, `condition`; each is rejected with `fieldErrors` when sent for the wrong
  `type`. New error code `CARE_DETAIL_TYPE_MISMATCH`, with `errors.*` entries in both locales.
- `GET /api/care-logs` returns the new fields, plus a derived `sleepMinutes` for ended sleeps.
- `POST /api/care-logs/{id}/end-sleep` sets `endedAt` to now (SGT) on an open sleep. A dedicated
  endpoint rather than a plain update, so the common "woke up" tap cannot rewrite the start time it
  was read from. Codes: `CARE_LOG_NOT_SLEEP` (400), `SLEEP_ALREADY_ENDED` (409),
  `SLEEP_WINDOW_INVALID` (400), each with `errors.*` entries in both locales.
- Timestamps keep the `+08:00` serialization.

## UI
- The entry dialog is per-type: feeding shows method buttons then the ml / minutes fields the method
  calls for; sleep shows start and end time fields with the derived duration beside them; diaper
  shows the four condition buttons. `note` remains below, now for prose only.
- The day list shows the structured value as its primary text — `110 ml`, `15 min · 60 ml`,
  `2h 10m`, `Dirty` — with the note secondary. A breastfeed carrying minutes but no volume reads
  `15 min · amount not recorded`, never as a feed of nothing.
- **A note is shown only when it says something the fields do not.** The migrated notes are kept in
  the database as the audit trail for V11's parse, but a row rendering `20 ml · 20ml` repeats
  itself; a note that is nothing but the quantity is not displayed, while prose
  (`Baby fell asleep`) always is. This is a display rule — it deletes nothing.
- The feeding `notePlaceholder` strings lose their `e.g. 150ml` hints; that information now has
  fields of its own.
- Quick-log buttons are unchanged: one tap still creates a bare entry, and the dialog fills in detail.

## Acceptance criteria
- V10 and V11 apply cleanly to a restore of the production database, and the app boots with
  `ddl-auto: validate` against the result.
- After migration, of the 560 feeding rows: **415** have `amountMl` and `BOTTLE`, **136** have
  `durationMin` and `BREAST`, **5** are `BOTH`, and 4 (the empty notes) have neither. All 556
  rows that had a note retain it verbatim.
- Overall measured coverage is **419 of 560 feeds (74.8%)**, totalling 30,645 ml and 2,067 breast
  minutes over 59 days. These are the figures spec 09's aggregate must reproduce.
- Every row in the table above holds the stated value, verified by id.
- 11 diaper rows have a `condition`, 28 are null; all 8 sleep rows have `startedAt == loggedAt` and
  a null `endedAt`.
- No feeding row receives an `amountMl` that is not present in its note text.
- A new breast feed records 15 minutes and 60 ml together and both survive a reload.
- Sending `amountMl` on a `SLEEP` entry returns 400 with `CARE_DETAIL_TYPE_MISMATCH`.
- An open sleep shows as ongoing; ending it sets the end time and the list shows the duration.
- Both locales render every new label, with no untranslated key.
