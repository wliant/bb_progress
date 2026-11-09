package com.bb.progress.carelog;

import java.time.LocalDate;

/**
 * One grouped row from {@link CareLogRepository#aggregateByDay}. The query aliases every
 * column in camelCase and quoted, so Postgres preserves the case and Spring Data can bind
 * each one to the matching accessor.
 */
public interface CareAggregateRow {

    LocalDate getDay();

    int getAmountMl();

    int getFeedsTotal();

    int getFeedsWithAmount();

    int getBreastMinutes();

    long getSleepMinutes();

    int getSleepSessions();

    int getOpenSleeps();

    int getDiaperTotal();

    int getDiaperClean();

    int getDiaperSlightlyDirty();

    int getDiaperDirty();

    int getDiaperVeryDirty();

    int getDiaperUnknown();
}
