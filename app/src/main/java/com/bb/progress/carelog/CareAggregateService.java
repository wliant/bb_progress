package com.bb.progress.carelog;

import com.bb.progress.carelog.CareAggregateDtos.AggregateSummary;
import com.bb.progress.carelog.CareAggregateDtos.CareAggregateResponse;
import com.bb.progress.carelog.CareAggregateDtos.DayAggregate;
import com.bb.progress.common.ApiException;
import com.bb.progress.common.Sgt;
import java.time.Instant;
import java.time.LocalDate;
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

    public CareAggregateService(CareLogRepository repository) {
        this.repository = repository;
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

        // Every day in the window is emitted, so a gap in the chart is a real gap rather
        // than a missing point the chart library would bridge with a straight line.
        List<DayAggregate> days = new ArrayList<>((int) span);
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(toDay(day, rows.get(day)));
        }
        return new CareAggregateResponse(start, end, days, summarize(days));
    }

    private static DayAggregate toDay(LocalDate date, CareAggregateRow row) {
        if (row == null) {
            return new DayAggregate(date, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
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
                row.getDiaperUnknown());
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
