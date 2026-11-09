package com.bb.progress.carelog;

import com.bb.progress.carelog.CareAggregateDtos.AggregateSummary;
import com.bb.progress.carelog.CareAggregateDtos.CareAggregateResponse;
import com.bb.progress.carelog.CareAggregateDtos.DayAggregate;
import com.bb.progress.baby.BabyRepository;
import com.bb.progress.carelog.CareAggregateDtos.DayComparable;
import com.bb.progress.carelog.CareAggregateDtos.DayReference;
import com.bb.progress.common.ApiException;
import com.bb.progress.common.Sgt;
import com.bb.progress.growth.GrowthRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareAggregateService {

    /** Wide enough for "all" on a record that starts at birth, bounded so one request cannot scan forever. */
    private static final long MAX_WINDOW_DAYS = 400;

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final CareLogRepository repository;
    private final GrowthRecordRepository growthRecords;
    private final BabyRepository babies;

    public CareAggregateService(CareLogRepository repository,
            GrowthRecordRepository growthRecords, BabyRepository babies) {
        this.repository = repository;
        this.growthRecords = growthRecords;
        this.babies = babies;
    }

    @Transactional(readOnly = true)
    public CareAggregateResponse aggregate(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : Sgt.today();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("CARE_AGGREGATE_RANGE_INVALID",
                    "Range start must not be after its end");
        }
        long span = end.toEpochDay() - start.toEpochDay() + 1;
        if (span > MAX_WINDOW_DAYS) {
            throw ApiException.badRequest("CARE_AGGREGATE_RANGE_INVALID",
                    "Range must not exceed " + MAX_WINDOW_DAYS + " days");
        }

        Instant fromInstant = start.atStartOfDay(Sgt.ZONE).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(Sgt.ZONE).toInstant();

        Map<LocalDate, CareAggregateRow> rows = new HashMap<>();
        for (CareAggregateRow row : repository.aggregateByDay(fromInstant, toInstant)) {
            rows.put(row.getDay(), row);
        }

        WeightTimeline weights = new WeightTimeline(growthRecords.findAllByOrderByMeasuredOnAsc());
        // Unlike a write, this read does not require a profile: without one there is simply
        // no age to band a reference by, and the counts are still worth returning.
        LocalDate dateOfBirth = babies.findFirstByOrderByCreatedAtAsc()
                .map(baby -> baby.getDateOfBirth())
                .orElse(null);
        LocalDate today = Sgt.today();

        // Every day in the window is emitted, so a gap in the chart is a real gap rather
        // than a missing point the chart library would bridge with a straight line.
        List<DayAggregate> days = new ArrayList<>((int) span);
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(toDay(day, rows.get(day), weights, dateOfBirth, today));
        }
        return new CareAggregateResponse(start, end, days, summarize(days));
    }

    private static DayAggregate toDay(LocalDate date, CareAggregateRow row,
            WeightTimeline weights, LocalDate dateOfBirth, LocalDate today) {
        DayReference reference = referenceFor(date, weights, dateOfBirth);
        if (row == null) {
            return new DayAggregate(date, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    reference, new DayComparable(false, false, false));
        }
        // A reference is drawn as context on every day, but a verdict is only earned where
        // the recorded data supports one -- and never on a day still in progress.
        boolean past = date.isBefore(today);
        DayComparable comparable = new DayComparable(
                past && row.getFeedsTotal() > 0 && row.getFeedsWithAmount() == row.getFeedsTotal(),
                // An open nap is an unknown number of minutes, exactly like an unmeasured feed.
                past && row.getSleepSessions() > 0 && row.getOpenSleeps() == 0,
                past && row.getDiaperTotal() > 0 && row.getWetUnknown() == 0);
        return new DayAggregate(date,
                row.getAmountMl(),
                row.getFeedsTotal(),
                row.getFeedsWithAmount(),
                row.getBreastMinutes(),
                // A day is partial when some feed's volume was never measured. With no
                // feeds at all there is nothing missing, so it is not partial.
                row.getFeedsWithAmount() < row.getFeedsTotal(),
                row.getSleepMinutes(),
                row.getSleepSessions(),
                row.getOpenSleeps(),
                row.getDiaperTotal(),
                row.getDiaperClean(),
                row.getDiaperSlightlyDirty(),
                row.getDiaperDirty(),
                row.getDiaperVeryDirty(),
                row.getDiaperUnknown(),
                row.getWetCount(),
                row.getWetUnknown(),
                reference,
                comparable);
    }

    /**
     * The published ranges for one day. Intake needs a usable weight, so it is null when
     * there are no growth records or the last one has gone stale; the sleep band needs
     * only an age, and the wet floor is a constant.
     */
    private static DayReference referenceFor(LocalDate date, WeightTimeline weights,
            LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        int ageMonths = (int) ChronoUnit.MONTHS.between(dateOfBirth, date);
        if (ageMonths < 0) {
            return null;
        }
        WeightTimeline.DailyWeight weight = weights.on(date);
        return new DayReference(
                weight == null ? null : Math.round(weight.weightKg() * 100) / 100.0,
                weight != null && weight.estimated(),
                weight == null ? null : CareReference.intakeMl(weight.weightKg(), ageMonths),
                CareReference.sleepMinutes(ageMonths),
                CareReference.MIN_WET_NAPPIES_PER_DAY);
    }

    private static AggregateSummary summarize(List<DayAggregate> days) {
        int amountMl = 0;
        int feedsTotal = 0;
        int feedsWithAmount = 0;
        int breastMinutes = 0;
        long sleepMinutes = 0;
        int diaperTotal = 0;
        int daysWithData = 0;
        for (DayAggregate day : days) {
            amountMl += day.amountMl();
            feedsTotal += day.feedsTotal();
            feedsWithAmount += day.feedsWithAmount();
            breastMinutes += day.breastMinutes();
            sleepMinutes += day.sleepMinutes();
            diaperTotal += day.diaperTotal();
            if (day.feedsTotal() > 0 || day.sleepSessions() > 0 || day.diaperTotal() > 0) {
                daysWithData++;
            }
        }
        return new AggregateSummary(amountMl, feedsTotal, feedsWithAmount, breastMinutes,
                sleepMinutes, diaperTotal, daysWithData,
                feedsTotal == 0 ? 0 : round1(100.0 * feedsWithAmount / feedsTotal),
                perDay(amountMl, daysWithData),
                perDay(feedsTotal, daysWithData),
                perDay(sleepMinutes, daysWithData));
    }

    /** Averaged over days that actually hold entries, not the width of the window. */
    private static double perDay(double total, int daysWithData) {
        return daysWithData == 0 ? 0 : round1(total / daysWithData);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
