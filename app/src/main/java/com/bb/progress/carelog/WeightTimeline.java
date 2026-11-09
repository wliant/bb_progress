package com.bb.progress.carelog;

import com.bb.progress.growth.GrowthRecord;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily weight read off the growth records (spec 10).
 *
 * <p>Between two measurements the weight is interpolated linearly. Before the first it is
 * that first measurement. After the last it is <b>held flat</b> and marked estimated —
 * never extrapolated, because projecting a growth trend forward compounds its own error,
 * and the intake band would drift up without anyone weighing the baby.
 */
public final class WeightTimeline {

    public record DailyWeight(double weightKg, boolean estimated) {
    }

    private final List<GrowthRecord> measurements;

    public WeightTimeline(List<GrowthRecord> records) {
        // Only records that actually carry a weight; height-only entries say nothing here.
        List<GrowthRecord> weighed = new ArrayList<>(records.stream()
                .filter(r -> r.getWeightKg() != null)
                .toList());
        weighed.sort((a, b) -> a.getMeasuredOn().compareTo(b.getMeasuredOn()));
        this.measurements = List.copyOf(weighed);
    }

    public boolean isEmpty() {
        return measurements.isEmpty();
    }

    /** The weight on a day, or null when no usable measurement supports it. */
    public DailyWeight on(LocalDate date) {
        if (measurements.isEmpty()) {
            return null;
        }
        GrowthRecord first = measurements.get(0);
        if (!date.isAfter(first.getMeasuredOn())) {
            // Before the record starts: the earliest known weight, flagged as a stand-in.
            return new DailyWeight(first.getWeightKg().doubleValue(),
                    date.isBefore(first.getMeasuredOn()));
        }
        GrowthRecord last = measurements.get(measurements.size() - 1);
        if (date.isAfter(last.getMeasuredOn())) {
            long stale = date.toEpochDay() - last.getMeasuredOn().toEpochDay();
            if (stale > CareReference.MAX_WEIGHT_AGE_DAYS) {
                return null;
            }
            return new DailyWeight(last.getWeightKg().doubleValue(), true);
        }
        for (int i = 1; i < measurements.size(); i++) {
            GrowthRecord after = measurements.get(i);
            if (!date.isAfter(after.getMeasuredOn())) {
                GrowthRecord before = measurements.get(i - 1);
                long span = after.getMeasuredOn().toEpochDay() - before.getMeasuredOn().toEpochDay();
                long into = date.toEpochDay() - before.getMeasuredOn().toEpochDay();
                double from = before.getWeightKg().doubleValue();
                double to = after.getWeightKg().doubleValue();
                double weight = span == 0 ? to : from + (to - from) * ((double) into / span);
                // Interpolated between two real measurements: good enough to compare against.
                return new DailyWeight(weight, into != 0 && into != span);
            }
        }
        return null;
    }
}
