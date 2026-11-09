package com.bb.progress.carelog;

import java.time.LocalDate;
import java.util.List;

public final class CareAggregateDtos {

    private CareAggregateDtos() {
    }

    /** The published ranges for this day, or null where none can be produced. */
    public record DayReference(
            Double weightKg,
            /** True when the weight is a stand-in rather than interpolated between measurements. */
            boolean weightEstimated,
            CareReference.Range intakeMl,
            CareReference.Range sleepMinutes,
            int wetNappiesMin) {
    }

    /**
     * Whether each figure may be compared to its reference at all.
     *
     * <p>A reference is always drawn as context, but a verdict ("below range") is only
     * rendered where the recorded data actually supports one. Today is never comparable:
     * the day is still in progress.
     */
    public record DayComparable(boolean intake, boolean sleep, boolean wet) {
    }

    /**
     * One SGT calendar day.
     *
     * <p>{@code amountMl} sums only the feeds that recorded a volume. A breastfeed whose
     * amount was never measured is not a zero-volume feed, so {@code feedsTotal} and
     * {@code feedsWithAmount} travel with every sum and {@code partial} marks a day whose
     * total is therefore incomplete. Roughly a quarter of the historical feeds are in that
     * position, so a bare total would read lowest on the heaviest breastfeeding days.
     */
    public record DayAggregate(
            LocalDate date,
            int amountMl,
            int feedsTotal,
            int feedsWithAmount,
            int breastMinutes,
            boolean partial,
            long sleepMinutes,
            int sleepSessions,
            int openSleeps,
            int diaperTotal,
            int diaperClean,
            int diaperSlightlyDirty,
            int diaperDirty,
            int diaperVeryDirty,
            int diaperUnknown,
            /** Changes flagged wet. Only meaningful when `comparable.wet` is true. */
            int wetCount,
            /** Changes with no wet/dry recorded — any at all makes the count incomplete. */
            int wetUnknown,
            DayReference reference,
            DayComparable comparable) {
    }

    /**
     * Totals across the requested window. Averages divide by {@code daysWithData} rather
     * than the window length, so a range reaching back before the first entry does not
     * drag every average down.
     */
    public record AggregateSummary(
            int amountMl,
            int feedsTotal,
            int feedsWithAmount,
            int breastMinutes,
            long sleepMinutes,
            int diaperTotal,
            int daysWithData,
            /** Share of feeds carrying a volume, 0-100, one decimal. */
            double coveragePct,
            double avgAmountMlPerDay,
            double avgFeedsPerDay,
            double avgSleepMinutesPerDay) {
    }

    public record CareAggregateResponse(
            LocalDate from,
            LocalDate to,
            List<DayAggregate> days,
            AggregateSummary summary) {
    }
}
