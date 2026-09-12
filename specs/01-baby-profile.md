# Spec 01 — Baby Profile

## Overview
Manage the single baby's profile: name, date of birth, gender, and one profile photo. The app supports exactly one baby; the profile is created once and edited thereafter (upsert semantics).

## Requirements
- Fields: name (required, 1–100 chars), date of birth (required, not in the future), gender (required, MALE | FEMALE), profile photo (optional).
- Single-baby invariant: `PUT /api/baby` creates the profile if absent, otherwise updates it. There is never more than one baby row.
- Photo: jpeg/png/webp, max 10 MB. HEIC rejected with a localized, actionable error. Uploading a new photo replaces (and deletes) the old file. Stored on the photo docker volume; DB stores relative path only.
- Baby's age (derived from DOB, computed in SGT) is shown on the home page and used by growth charts and milestone grouping.

## API
- `GET /api/baby` → 200 with profile, or 404 `{code: "BABY_NOT_FOUND"}` if not yet created.
- `PUT /api/baby` body `{name, dateOfBirth, gender}` → 200 with saved profile.
- `PUT /api/baby/photo` multipart `file` → 200. Errors: 400 `UNSUPPORTED_MEDIA_TYPE` (wrong type), 413 (too large).
- `GET /api/baby/photo` → image bytes, or 404 if none.

## UI
- `/profile` page: form with name, DOB (date input), gender selector, photo upload with preview.
- First launch (no profile): home page prompts to create the profile and links to `/profile`.
- All labels/messages in zh-CN and en.

## Acceptance criteria
- Creating, editing, and reloading the profile persists all fields.
- Uploaded photo displays on profile and home pages after reload.
- Invalid input (empty name, future DOB) shows localized field errors and is rejected by the API.
