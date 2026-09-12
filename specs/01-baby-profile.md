# Spec 01 — Baby Profile

## Overview
Manage the single baby's profile: name, date of birth, gender, and one profile photo. The app supports exactly one baby; the profile is created once and edited thereafter (upsert semantics).

## Requirements
- Fields: name (required, 1–100 chars), date of birth (required, not in the future), time of birth (optional, HH:mm), gender (required, MALE | FEMALE), profile photo (optional).
- **Birth measurements** (all optional): weight, length, head circumference. They are stored as the
  growth record dated at the date of birth, flagged as the birth record, so they appear on the growth
  chart as the first point without being entered twice. Editing them in the profile updates that
  record; clearing all three removes it.
- The birth record moves with the date of birth: correcting the date of birth re-dates it rather than
  being blocked by the "date of birth is after existing records" rule.
- Single-baby invariant: `PUT /api/baby` creates the profile if absent, otherwise updates it. There is never more than one baby row.
- Photo: jpeg/png/webp, max 10 MB. HEIC rejected with a localized, actionable error. Uploading a new photo replaces (and deletes) the old file. Stored on the photo docker volume; DB stores relative path only.
- Baby's age (derived from DOB, computed in SGT) is shown on the home page and used by growth charts and milestone grouping.

## API
- `GET /api/baby` → 200 with profile (including `timeOfBirth` and the birth measurements), or 404 `{code: "BABY_NOT_FOUND"}` if not yet created.
- `PUT /api/baby` body `{name, dateOfBirth, timeOfBirth?, gender, birthWeightKg?, birthLengthCm?, birthHeadCircumferenceCm?}` → 200 with saved profile.
- `PUT /api/baby/photo` multipart `file` → 200. Errors: 400 `UNSUPPORTED_MEDIA_TYPE` (wrong type), 413 (too large).
- `GET /api/baby/photo` → image bytes, or 404 if none.

## UI
- `/profile` page: form with name, DOB (date input), time of birth (time input, optional), gender selector,
  an optional "at birth" measurements group (weight / length / head circumference), and photo upload with preview.
- Time of birth is shown alongside the birth date on the profile and home pages when set.
- First launch (no profile): home page prompts to create the profile and links to `/profile`.
- All labels/messages in zh-CN and en.

## Acceptance criteria
- Creating, editing, and reloading the profile persists all fields, time of birth included.
- Birth measurements entered in the profile appear as the first point on the growth chart and in the
  growth record list, labelled as the birth record.
- Correcting the date of birth re-dates the birth record instead of being rejected.
- Uploaded photo displays on profile and home pages after reload.
- Invalid input (empty name, future DOB) shows localized field errors and is rejected by the API.
