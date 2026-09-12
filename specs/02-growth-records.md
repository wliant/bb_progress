# Spec 02 — Growth Records

## Overview
Log the baby's weight, height/length, and head circumference over time; view them as a list and as charts overlaid with WHO Child Growth Standards percentile curves (P3/P15/P50/P85/P97), gender-specific.

## Requirements
- A growth record has: measured_on (date, required, unique — one record per day, not in the future, not before DOB), weight_kg (0.3–40, 2dp), height_cm (20–150, 1dp), head_circumference_cm (20–70, 1dp), note (optional). At least one measurement must be present.
- Records are editable and deletable.
- At most one record is flagged as the **birth record**. It is created and maintained from the baby profile's
  birth measurements (see spec 01), is dated at the date of birth, and is labelled as such in the list.
- Charts: one per measure (weight / height / head circumference). X axis = age in months (from DOB to measured_on), Y = value. Baby's data drawn as a bold line with dots; WHO percentile curves P3/P15/P50/P85/P97 as muted dashed lines, chosen by the baby's gender.
- WHO reference: WHO Child Growth Standards LMS tables, bundled as resources (3 measures × 2 genders, 0–36 months by month). Percentile value = M·(1+L·S·z)^(1/L). Attribution in README (CC BY-NC-SA).

## API
- `GET /api/growth-records` → all records sorted by measured_on asc.
- `POST /api/growth-records`, `PUT /api/growth-records/{id}`, `DELETE /api/growth-records/{id}`.
- `GET /api/growth-standards?gender=MALE|FEMALE&measure=WEIGHT|HEIGHT|HEAD_CIRCUMFERENCE` → `{measure, gender, curves: {p3:[{ageMonths, value}], p15:[...], p50:[...], p85:[...], p97:[...]}}`.

## UI
- `/growth` page: tabs per measure; chart on top, record list below; add/edit form (date + three optional measures + note).
- Duplicate-date submission shows a localized error.

## Acceptance criteria
- Adding a record shows it in the list and on the chart without reload.
- Percentile curves render for the baby's gender; golden-value unit tests verify LMS math against WHO published percentile tables (several ages, both genders).
- Validation errors (no measurement given, future date, out-of-range values, duplicate date) are localized.
