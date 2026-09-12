# Spec 04 — Daily Care Logs

## Overview
Quick, minimal logging of daily care events: feeding, sleep, diaper change. One tap creates an entry timestamped "now" (SGT); an optional free-text note can be added.

## Requirements
- A care log has: type (FEEDING | SLEEP | DIAPER, required), logged_at (timestamp, defaults to now, editable, not in future), note (optional, ≤500 chars).
- Entries can be edited (time, note) and deleted.
- Day view: entries for a chosen date (default today in SGT), newest first, with per-type counts for the day.
- Quick logging: large one-tap buttons for the three types on the home page and on the care page. A tap logs immediately with the current SGT time; a toast confirms with an option to add a note.

## API
- `GET /api/care-logs?date=YYYY-MM-DD&type=FEEDING` → entries for that date (SGT day boundaries), date defaults to today SGT, type optional.
- `POST /api/care-logs` body `{type, loggedAt?, note?}` → 201; loggedAt defaults to server now.
- `PUT /api/care-logs/{id}` body `{loggedAt, note}`; `DELETE /api/care-logs/{id}`.
- Timestamps serialize as ISO-8601 with +08:00 offset.

## UI
- `/care` page: date picker (default today), three quick-log buttons, list of the day's entries with type icon, time (HH:mm SGT), note.
- Home page: the same three quick-log buttons, today's counts, and a **recent entries** list showing the
  latest few of today's logs, plus a link to the full day view.
- Entries are editable from either list: tapping an entry opens a dialog to adjust the time, add or change
  the note, or delete it. This is the path for enriching a one-tap entry — tap 喂奶, then tap the new row
  to add the amount as a note.

## Acceptance criteria
- Tapping a quick button creates an entry visible immediately with the current SGT time, on the home page
  as well as the care page.
- Tapping that entry from the home page opens the edit dialog, and a note added there persists.
- Day boundaries are SGT: an entry at 23:50 SGT appears on that SGT date regardless of server/browser TZ.
- Counts per type update as entries are added/removed.
