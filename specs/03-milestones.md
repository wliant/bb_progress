# Spec 03 — Milestones

## Overview
Track developmental milestones against a predefined, bilingual checklist based on CDC "Learn the Signs. Act Early." milestones. No custom milestones.

## Requirements
- Milestone definitions are seeded via Flyway migration and read-only: stable text id (e.g. `2m-social-smiles`), age_months ∈ {2, 4, 6, 9, 12, 15, 18, 24, 30, 36}, category (SOCIAL | LANGUAGE | COGNITIVE | MOTOR), title_en, title_zh, sort_order. Translations come from CDC's official Simplified Chinese checklists.
- Checking off a milestone records: achieved_on (date, required, not in future, not before DOB), optional note, optional photo (same rules as profile photo). One achievement per definition; can be edited or removed (un-checked). Removing deletes the photo file.
- UI groups milestones by age (accordion/sections per age group), shows category, achieved state with date, and progress per group (e.g. 3/8).

## API
- `GET /api/milestones` → definitions joined with achievement state, grouped by age_months, both titles included (frontend picks by current language).
- `PUT /api/milestones/{definitionId}/achievement` body `{achievedOn, note?}` → create or update.
- `DELETE /api/milestones/{definitionId}/achievement` → un-check.
- `PUT /api/milestones/{definitionId}/achievement/photo` multipart; `GET .../photo` → bytes.

## UI
- `/milestones` page: sections per age group (2个月 / 2 months, ...), checkbox rows; tapping opens a dialog to set date, note, photo.
- Titles switch instantly with the language switcher.

## Acceptance criteria
- Seed data integrity: integration test validates definition count per age group and that every row has non-empty title_en and title_zh.
- Check-off persists across reload; un-check clears it.
- Language switch flips milestone titles between English and Chinese without reload.
