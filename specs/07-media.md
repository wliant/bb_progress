# Spec 07 — Media attachments

## Overview
Care-log entries and milestone achievements can each carry any number of media attachments —
photos, videos and voice recordings. This replaces the single photo each previously allowed.
The baby's profile picture is unaffected and stays a single photo.

## Requirements
- **Many per entry.** An entry has zero or more attachments, kept in the order they were added.
  Several files can be chosen at once.
- **Three kinds**, decided from the upload's content type:
  - `PHOTO` — jpeg, png, webp. Re-encoded to JPEG at ≤1 MB as before, EXIF orientation applied.
  - `VIDEO` — mp4, quicktime, webm. Stored as uploaded; the app does not transcode.
  - `AUDIO` — mpeg/mp3, mp4/m4a, aac, wav, ogg, webm. Stored as uploaded.
- Anything else is refused with `UNSUPPORTED_MEDIA_TYPE`.
- Uploads accept up to 200 MB so a phone video goes through. Only photos are compressed; a video
  or a voice note is kept as recorded because the app has no transcoder.
- Attachments are stored as objects like any other media, keyed `media/<uuid>.<ext>`. Deleting an
  attachment, its entry, or a milestone achievement deletes the underlying objects.
- Thumbnails exist for photos only (`?size=thumb`). Video and audio have none; the UI shows a kind
  icon rather than inventing a frame.

## API
- `GET /api/care-logs/{id}/media`, `POST /api/care-logs/{id}/media` (multipart `files`, repeatable)
- `GET /api/milestones/{definitionId}/achievement/media`, `POST .../media`
- `GET /api/media/{mediaId}/content` (+ `?size=thumb` for photos), `DELETE /api/media/{mediaId}`
- `GET /api/media` — everything, newest first, for the media page
- Care-log and milestone responses embed their attachments as
  `media: [{id, kind, contentType, url, thumbnailUrl}]`.

## UI
- Both the care-log dialog and the milestone dialog use one shared picker: a strip of existing
  attachments with a remove control on each, and one button to add more. Photos preview as
  thumbnails, videos and voice notes as labelled tiles.
- On a milestone that is not yet achieved, chosen files are held and uploaded when it is saved,
  exactly as the single photo used to be.
- The **Media** page (formerly Photos) lists everything newest first, grouped by SGT date. Photos
  are thumbnails; videos and voice notes show their kind. Opening one plays video and audio inline
  with native controls.

## Acceptance criteria
- Several photos can be attached to one care-log entry, all appearing on the media page.
- A video uploads and plays back; a voice note uploads and plays back.
- An unsupported type is refused with a localized message.
- Removing one attachment leaves the others intact; deleting the entry removes all of them and
  their stored objects.
