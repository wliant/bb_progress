# Spec 04 — Daily Care Logs

## Overview
Quick, minimal logging of daily care events: feeding, sleep, diaper change. One tap creates an entry timestamped "now" (SGT); an optional free-text note can be added.

## Requirements
- A care log has: type (FEEDING | SLEEP | DIAPER, required), logged_at (timestamp, defaults to now, editable, not in future), note (optional, ≤500 chars), photo (optional).
- **Photo**: one per entry, added or replaced from the entry's edit dialog, and removable.
  Uploads accept up to 25 MB so a photo straight off a phone goes through untouched; the server
  then re-encodes it to JPEG at no more than 1 MB before storing. EXIF orientation is applied
  during that re-encode so portrait photos are not stored sideways.
  Deleting an entry deletes its stored object.
- Entries can be edited (time, note) and deleted.
- Day view: entries for a chosen date (default today in SGT), newest first, with per-type counts for the day.
- Quick logging: large one-tap buttons for the three types on the home page and on the care page. A tap logs immediately with the current SGT time; a toast confirms with an option to add a note.
- **Past-day quick logging**: the care page keeps the quick-log panel when a past date is selected, so a
  forgotten entry can be backfilled. On a past date the panel shows a time field (HH:mm), prefilled with
  the current SGT time of day. A tap then logs at `<selected date> <time>` in SGT (`loggedAt` sent as
  `YYYY-MM-DDTHH:mm:00+08:00`). Today keeps the one-tap "now" behaviour with no time field, and the home
  page is unchanged. Future dates cannot be picked, so no future timestamp can arise.

## API
- `GET /api/care-logs?date=YYYY-MM-DD&type=FEEDING` → entries for that date (SGT day boundaries), date defaults to today SGT, type optional.
- `POST /api/care-logs` body `{type, loggedAt?, note?}` → 201; loggedAt defaults to server now.
- `PUT /api/care-logs/{id}` body `{loggedAt, note}`; `DELETE /api/care-logs/{id}`.
- `PUT /api/care-logs/{id}/photo` multipart `file`; `GET /api/care-logs/{id}/photo`; `DELETE /api/care-logs/{id}/photo`.
- Timestamps serialize as ISO-8601 with +08:00 offset.

## UI
- `/care` page: date picker (default today), three quick-log buttons, list of the day's entries with type icon, time (HH:mm SGT), note.
  The quick-log panel shows on every selectable date; on a past date its heading names the date and a
  time field sits above the buttons. The counts label reads "Today" only for today and the selected date
  otherwise.
- Home page: the same three quick-log buttons, today's counts, and a **recent entries** list showing the
  latest few of today's logs, plus a link to the full day view.
- Entries are editable from either list: tapping an entry opens a dialog to adjust the time, add or change
  the note, or delete it. This is the path for enriching a one-tap entry — tap 喂奶, then tap the new row
  to add the amount as a note.

## Acceptance criteria
- Tapping a quick button creates an entry visible immediately with the current SGT time, on the home page
  as well as the care page.
- Tapping that entry from the home page opens the edit dialog, and a note added there persists.
- A multi-megabyte phone photo uploads successfully and is stored at 1 MB or less; the served image
  is right way up. Entries with a photo show a thumbnail in the list.
- Day boundaries are SGT: an entry at 23:50 SGT appears on that SGT date regardless of server/browser TZ.
- Counts per type update as entries are added/removed.
- On the care page with a past date selected, the quick-log panel is visible with a time field. Tapping a
  button creates an entry on that date at the chosen SGT time, and it appears immediately in that day's list
  and counts.
- With today selected, the panel has no time field and a tap logs the current time, as before.
