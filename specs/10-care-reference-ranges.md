# Spec 10 — Reference Ranges on the Trends View

## Overview
The trends view (spec 09) shows what was recorded. This adds what is typical for a baby of
Emmy's weight and age, so a day can be read in context: a daily intake band derived from her
interpolated weight, a sleep band from her age, and a floor for wet nappies.

## This is reference information, not medical advice
The figures below are commonly published general ranges for healthy term infants. They are not
specific to this baby, and a paediatrician's figure for her overrides anything the app draws.
Every chart carrying a reference says so once, plainly, and the whole reference layer can be
switched off.

## The comparability problem — the reason this spec is not three reference lines
Each reference compares against data that is systematically incomplete, and differently so:

| | recorded | what the reference measures | gap |
|---|---|---|---|
| Intake | 419 of 560 feeds carry a volume | total milk taken | 54 of 59 days have at least one unmeasured feed |
| Sleep | 0 minutes (all 8 entries open-ended) | total sleep across 24 h | essentially everything |
| Diapers | 0.66 changes logged per day | wet nappies per day (6+) | the app records condition, not wetness |

Drawn naively, all three would show large deficits on nearly every day that are artefacts of
recording, not facts about the baby — and the intake chart would look worst on exactly the days
with the most breastfeeding. So:

- **A reference is drawn as context; a verdict is only rendered when the day is comparable.**
- A day is never comparable if it is **today** — the day is still in progress.
- Non-comparable days say why ("5 feeds unmeasured"), never "below range".
- When nothing in the window is comparable, the chart says so instead of drawing a gap.
- When a chart has **no data at all**, it keeps the existing "nothing recorded in this range"
  state and draws no band — a reference floating over an empty grid says less than the sentence
  does. (The spec first called for a greyed band there; the empty state is the stronger answer.)

## Requirements

### Weight, interpolated
- Daily weight comes from the growth records: linear interpolation between the two surrounding
  measurements, birth weight before the first, and the last measurement **held flat** afterwards —
  never extrapolated, since a growth trend projected forward is a guess that compounds.
- A day whose weight is held flat is marked `estimated`. Beyond **30 days** past the last
  measurement the weight is treated as unusable and no intake reference is produced.
- With no growth records at all there is no intake reference, and the chart says so.

### Intake band (ml/day)
`weightKg × ml/kg/day`, as a band, from a single table in one file so it is easy to correct:

| age | ml/kg/day |
|---|---|
| 0–3 months | 120–180 |
| 3–6 months | 110–160 |
| 6–9 months | 90–130 |
| 9–12 months | 80–110 |

- Capped at **1000 ml/day**, beyond which the per-kg figure stops being meaningful.
- The band applies to milk *taken by bottle or expressed*. A directly breastfed baby regulates her
  own intake and volume is not the measure — which is the same reason an unmeasured breastfeed
  makes a day non-comparable.
- **Comparable** when `feedsWithAmount == feedsTotal`, `feedsTotal > 0`, and the day is past.

### Sleep band (hours/day)

| age | hours/day |
|---|---|
| 0–3 months | 14–17 |
| 4–11 months | 12–16 |

- The band is total sleep across 24 hours, including naps. The app only counts sleeps logged
  **with an end time**, so the chart is labelled "logged sleep" and the band is context.
- **Comparable** when the day has at least one closed sleep and no open ones — an open nap is an
  unknown number of minutes, exactly like an unmeasured feed.

### Wet nappies (floor, not a band)
- A **new `wet` flag** on a diaper entry (V12, nullable, DIAPER-only), set from a toggle in the
  entry dialog. Historical rows stay null; no backfill, because the old notes
  (`No poo`, `A little poo`) say nothing reliable about wetness.
- Reference is a **floor at 6 wet nappies/day** — the usual hydration indicator — drawn as a
  minimum line, not a band, since there is no meaningful upper bound.
- **Comparable** when every diaper entry that day has a non-null `wet` value, and the day is past.
- **Stated limitation**: this also assumes *every* change is logged, which no data can confirm.
  The chart says so. At today's 0.66 changes/day logged, it will not be comparable until the
  logging habit changes.

## API
- The aggregate response gains, per day:
  `reference: { weightKg, weightEstimated, intakeMl: {low, high} | null,
  sleepMinutes: {low, high} | null, wetNappies: {min} }`
  and `comparable: { intake, sleep, wet }` (booleans).
- `wetCount` joins the per-day diaper figures.
- `POST`/`PUT /api/care-logs` accept `wet` for a DIAPER entry, rejected for others with the
  existing `CARE_DETAIL_TYPE_MISMATCH`.
- No new endpoint: the trends view still makes one request.

## UI
- The intake and sleep charts gain a shaded reference band behind the bars; the wet-nappy chart
  gains a dashed minimum line. Each is named in the chart's legend.
- Tooltips on a comparable day add `within range` / `below range` / `above range`; on a
  non-comparable day they give the reason instead.
- A new **wet nappies per day** chart, rather than overloading the stacked condition chart.
- One line under the reference charts: general guidance, not advice for this baby, confirm with
  your paediatrician.
- A **"show reference ranges" toggle**, remembered locally, defaulting on. Off hides every band,
  line and verdict.
- Reference colors are a recessive neutral (`REFERENCE` in `careChartPalette.ts`) — the band is
  context and must never compete with the data in front of it. The wet-nappy series hue was run
  through the same validator as the rest.
- The band is drawn flat across the window (widest low, highest high in view) rather than stepped
  per-day: weight and age move slowly enough that a stepped edge would read as a second series.
- A note under the intake chart says when the band rests on a weight carried forward from an
  earlier measurement, naming the date, or that there is no usable weight at all.
- Both locales throughout.

## Acceptance criteria
- With Emmy at 4.7 kg and 2 months, the intake band reads 564–846 ml/day.
- Of the 59 days with feeds, exactly the 5 fully-measured ones carry an intake verdict; the other
  54 say how many feeds were unmeasured.
- Today never carries a verdict on any of the three.
- With 0 closed sleeps, the sleep chart shows "nothing recorded in this range" and never renders
  a deficit. With some closed sleeps but an open nap in the window, the band is drawn and the
  chart says nothing in it is comparable yet.
- A diaper entry records `wet`, it round-trips, and sending `wet` on a FEEDING entry returns 400
  `CARE_DETAIL_TYPE_MISMATCH`.
- A day with one wet-flagged and one unflagged change is not comparable.
- Deleting all growth records removes the intake band and leaves the chart readable.
- A weight 31+ days stale produces no intake band.
- The toggle hides every reference element — band, minimum line, legend entries, verdicts, notes
  and the disclaimer — and the preference survives a reload.
- V12 applies to the live database without touching any existing row's data.
- Both locales render every new label, verdict and note.
