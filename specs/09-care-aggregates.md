# Spec 09 — Care Aggregates

## Overview
Once care details are structured (spec 08), the day-by-day record can be totalled and trended:
how much the baby took, how often, how long she slept, how many changes. This spec covers the
aggregate endpoint and the view that reads it.

## The coverage problem — read this before designing any total
A feeding entry may record an amount, a duration, or neither. **Breast feeds without an ml figure
are not zero-volume feeds; their volume is unknown.** In the live data this is not an early-weeks
artefact that ages out — breast-only feeds appear on 48 of 59 recorded days, 1–6 per day, right up
to the present:

```
2026-10-24   520 ml over 12 feeds  — but 6 of those 12 have no ml recorded
2026-11-03   580 ml over 11 feeds  — 5 with no ml
2026-11-09   540 ml over  7 feeds  — 0 with no ml
```

A bare "total consumed" over that data is wrong by an unknown margin and, worse, dips on exactly
the days with the most breastfeeding — it would read as the baby eating less when she ate more.

Every total is therefore reported **with its coverage**, and the UI never shows a volume figure
without it:
- `amountMl` sums only over feeds that recorded one.
- `feedsTotal` and `feedsWithAmount` accompany every sum.
- A day where `feedsWithAmount < feedsTotal` is marked **partial** and rendered distinctly.

Against the live data this is 54 of the 59 days holding entries — not a handful. (An earlier
reading of 48 counted only days with a breast-only feed; the real figure also includes days
carrying a feed logged with no detail at all.)

## Requirements
- Daily aggregate, SGT day boundaries, over a requested date range:
  - Feeding: `amountMl` (sum, over measured feeds), `feedsTotal`, `feedsWithAmount`,
    `breastMinutes` (sum of `durationMin`), `partial` (boolean).
  - Sleep: `sleepMinutes` (sum of ended sleeps), `sleepSessions`, `openSleeps`. A sleep is
    attributed to the SGT day it **began**, so a nap crossing midnight is not split.
  - Diaper: `diaperTotal` and a count per condition, with unknown-condition counted separately.
- Days with no entries appear in the range with zero counts and `partial: false`, so a gap in the
  chart is a real gap and not a missing point.
- Range summary over the whole requested window: the same sums, plus per-day averages computed
  **over days with data only**, and the window's overall coverage.
- Default window: last 30 days ending today (SGT). Selectable 7 / 30 / 90 / all.

## API
- `GET /api/care-aggregates?from=YYYY-MM-DD&to=YYYY-MM-DD` → `{from, to, days: [...], summary: {...}}`,
  `from`/`to` inclusive and interpreted in SGT; defaults to the last 30 days.
- Rejects a range where `from > to`, or wider than 400 days, with the standard error contract
  (`CARE_AGGREGATE_RANGE_INVALID`, plus `errors.*` in both locales).
- Aggregation runs as a single grouped SQL query over `(type, logged_at)`, not by loading entries
  into the application.
- Read-only. The aggregate view owns no data, exactly as `/api/media` owns none — adding or editing
  happens on the care log entry, so any care-log mutation must invalidate `['care-aggregates']`
  alongside `['care-logs']`.

## UI
- New `/care/trends` route, reached from a tab on the care page.
- Range selector (7 / 30 / 90 / all), then:
  - **Daily intake** — bar chart of `amountMl` per day. Partial days are filled with a 45° hatch in
    the series hue, and the legend names them "partial — some feeds not measured". The tooltip
    states `520 ml from 6 of 12 feeds`, never a bare `520 ml`.
  - **Feeds per day** — its own chart, *not* a line overlaid on the intake axis. Millilitres and a
    feed count share no scale, and a second y-axis is the one chart form that reliably misleads;
    frequency is still the honest companion to a partial volume, just plotted separately.
  - **Breast minutes per day** — bar chart.
  - **Sleep per day** — bar chart of hours, with open sleeps excluded and noted.
  - **Diaper conditions** — stacked bars per day in the four conditions plus unknown.
- Summary tiles above the charts: total intake with its coverage, average per day, average feeds
  per day, average sleep, total changes.
- Charts use Recharts as the growth page does and render in both locales with SGT date labels.
- Colors live in `web/src/features/care/careChartPalette.ts` and are **validated, not chosen by
  eye** — each set passes the lightness, chroma, CVD-separation, normal-vision and contrast checks.
  Diaper condition is ordered, so it uses one sequential ramp (monotonic in lightness) rather than
  categorical hues; the single gray is reserved for "not recorded", which is an absence rather than
  a degree. Legends for the intake and diaper charts are hand-built rather than Recharts', because
  the hatch has to appear in the legend and because Recharts reorders a ramp's entries.
- Loading renders a skeleton; a failed read renders `<ErrorState>` in place, never an empty chart.
  A chart whose every value is zero says so in words instead of showing a blank grid.
- A **table view** toggles open under the charts, carrying every figure (and each partial day's
  measured/total) so nothing is reachable only by reading a color.

## Acceptance criteria
- `GET /api/care-aggregates` over the migrated production data reproduces the per-day volumes in the
  table above, including `feedsWithAmount` of 6 on 2026-10-24 and 7 of 7 on 2026-11-09.
- A day with no entries is present in `days` with zeroes and is not marked partial.
- A day whose every feed recorded an amount is **not** marked partial; a day with one unmeasured
  breast feed is.
- No UI surface shows a summed volume without its coverage alongside.
- Adding a feeding from the care page updates the trends view without a reload.
- Sleep minutes exclude an in-progress nap, and the view says one is open rather than silently
  dropping it. A nap from 23:30 to 01:00 counts 90 minutes on the day it started.
- `from=2026-11-01&to=2026-10-01` returns 400 `CARE_AGGREGATE_RANGE_INVALID`.
- Day boundaries are SGT: a 23:50 SGT feed counts on that SGT date regardless of server TZ.
- Both locales render every label, tile and legend with no untranslated key.
