# Spec 11 — Head Shape

## Overview
Infant head shape changes slowly. Flattening on one side (plagiocephaly) or across the back
(brachycephaly) is hard to judge from memory and easy to see when two photos weeks apart are put
side by side. Photos dropped into a care log do not serve that purpose — they are buried among
feedings and nappy changes, and the media gallery mixes everything together.

This is a dedicated page for the same photo, taken over and over: one entry per day, an optional
note for context, and any number of photos on each entry.

## Requirements
- **One entry per SGT day.** The day is the unit of comparison, so `recorded_on` is unique. Adding
  photos to a day that already has an entry adds to it rather than creating a second one.
- **Photos only.** A head shape entry is a visual record; video and audio are refused with
  `HEAD_SHAPE_PHOTOS_ONLY`, checked before anything is stored so a rejected upload leaves no
  orphaned object. This is narrower than care-log attachments on purpose — the page's
  whole value is a grid of comparable stills.
- **Many photos per day, each removable on its own.** Parents take the same head from several
  angles in one sitting; which angles, and how many, is left to them rather than imposed as a
  schema. Removing a photo never removes the day it belongs to.
- **Optional note**, up to 500 characters — "slept on the right all week", "first week in the
  helmet". Context, not measurement.
- **No measurements.** Cranial indices (CVAI, CI) need calipers and a repeatable landmark; a phone
  photo cannot produce one honestly, and a number that looks clinical but is not would be worse
  than no number. Deliberately out of scope.
- **Newest day first.** The most recent photo is the one being compared against; it leads.
- Entries are listed whole — there is no per-day fetch — because the page exists for comparison
  across days, not for reviewing one.
- `recorded_on` cannot be in the future (`RECORDED_ON_IN_FUTURE`), and a profile must exist before
  anything can be recorded (`BABY_NOT_FOUND`), the same precondition as every other feature.
- **Attachments reuse the `media` table.** Head shape photos are media like any other, so they are
  compressed, thumbnailed, streamed and deleted by the existing code, and they appear in the media
  gallery with their date. There is no second photo store.
- Empty state explains what the page is for rather than showing a blank page.

## API
- `GET /api/head-shape` → newest day first:
  `[{id, recordedOn, note, media: [{id, kind, contentType, url, thumbnailUrl}]}]`
- `POST /api/head-shape` `{recordedOn, note}` → 201. Creates the day, or updates the note of the
  day that already exists — the caller does not have to know which.
- `PUT /api/head-shape/{id}` `{recordedOn, note}` → the updated entry.
- `DELETE /api/head-shape/{id}` → 204. Removes the day and its photos, objects included.
- `GET /api/head-shape/{id}/media`, `POST /api/head-shape/{id}/media` (multipart, repeatable
  `files` part) — the same shape as the care-log media endpoints.
- `DELETE /api/media/{mediaId}` removes a single photo, unchanged from the existing contract.
- `GET /api/media` gains `source: HEAD_SHAPE` items, dated by `recordedOn`.

## UI
- New `/head-shape` route with its own nav entry in both the desktop sidebar and the mobile tab bar.
- A date field defaulting to today (bounded by the date of birth and today) and an "add photos"
  button: pick the day, pick the photos, done. Creating the entry is implicit.
- One card per day, newest first: the date, the note, and a responsive grid of square thumbnails
  (`?size=thumb`, lazy) — 2 columns on a phone, more on wider screens.
- Tapping a thumbnail opens the full-size photo; the viewer can delete that photo.
- Tapping the day's header opens an editor for the note, the photos, and deleting the day.
- Bilingual throughout, SGT dates.

## Acceptance criteria
- Adding photos for a day with no entry creates the entry; adding more to the same day appends to
  it rather than producing a second card.
- A video or audio file is refused with `HEAD_SHAPE_PHOTOS_ONLY` and nothing is stored.
- Deleting one photo leaves the day and its other photos intact.
- Deleting a day removes its rows and its stored objects — the bucket count drops.
- A head shape photo appears in the media gallery under its recorded date, and disappears from
  there when removed here.
- A future date is refused with `RECORDED_ON_IN_FUTURE`.
- The page, its empty state and its errors read correctly in both Chinese and English.
