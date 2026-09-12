# Spec 05 — Photo gallery

## Overview
One page collecting every photo in the app, newest first, so the baby's pictures can be browsed
without hunting through individual entries.

## Requirements
- Sources: care-log photos, milestone achievement photos, and the profile photo. There is no
  separate photo store — the gallery is a view over what the other features already hold.
- Grouped by SGT date, newest group first. The profile photo has no meaningful date of its own and
  is shown in its own group at the top.
- Each tile shows the photo and its context: the care type and note, the milestone title (in the
  current language), or "profile photo".
- Tapping a tile opens a full-size viewer with the context, the date/time, and a link through to the
  entry it belongs to, where it can be edited or removed. The gallery itself is read-only — photos
  are added from the entry they belong to.
- **Thumbnails**: tiles must not download the full ~1 MB images. Photos are served at a reduced size
  on request (`?size=thumb`, longest edge 320 px), generated on first use and cached as a sibling
  object beside the original. Tiles also load lazily.
- Empty state explains where photos come from rather than showing a blank page.

## API
- `GET /api/photos` → newest first:
  `[{id, source: PROFILE|MILESTONE|CARE_LOG, url, thumbnailUrl, takenOn, takenAt, careType?, titleEn?, titleZh?, note?}]`
- `GET /api/care-logs/{id}/photo?size=thumb`, `GET /api/milestones/{id}/achievement/photo?size=thumb`,
  `GET /api/baby/photo?size=thumb` — reduced-size variants of the existing endpoints, same caching
  headers and validators.

## UI
- New `/photos` route with its own nav entry, in both the desktop sidebar and the mobile tab bar.
- Responsive grid: 2 columns on a phone, more on wider screens.

## Acceptance criteria
- A photo added to a care log appears in the gallery under that entry's SGT date with its note.
- A milestone photo appears under its achieved date with the milestone title, and the title follows
  the language switcher.
- Removing a photo from its entry removes it from the gallery.
- Tiles request the thumbnail variant, and the thumbnail is materially smaller than the original.
