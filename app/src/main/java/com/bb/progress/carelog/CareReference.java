package com.bb.progress.carelog;

/**
 * Published general reference ranges for healthy term infants (spec 10).
 *
 * <p><b>These are not advice for any particular baby.</b> They are the commonly published
 * ranges, kept in this one file so a paediatrician's figure can replace them with a single
 * edit. Nothing else in the codebase hard-codes a reference number.
 *
 * <p>The intake figures describe milk taken <i>by bottle or expressed</i>. A directly
 * breastfed baby regulates her own intake and volume is not the measure — the same reason
 * an unmeasured breastfeed makes a day non-comparable.
 */
public final class CareReference {

    private CareReference() {
    }

    /** Beyond this, a per-kg figure stops being meaningful for a milk-fed infant. */
    public static final int MAX_INTAKE_ML_PER_DAY = 1000;

    /** The usual hydration indicator: at least this many wet nappies a day. */
    public static final int MIN_WET_NAPPIES_PER_DAY = 6;

    /**
     * A weight older than this is no longer a usable basis for an intake band. Weight is
     * held flat after the last measurement rather than extrapolated — a projected growth
     * trend compounds its own error — so a stale figure would quietly drift wrong.
     */
    public static final int MAX_WEIGHT_AGE_DAYS = 30;

    public record Range(int low, int high) {
    }

    /** Millilitres per kilogram per day, by age. */
    public static Range intakeMlPerKg(int ageMonths) {
        if (ageMonths < 3) {
            return new Range(120, 180);
        }
        if (ageMonths < 6) {
            return new Range(110, 160);
        }
        if (ageMonths < 9) {
            return new Range(90, 130);
        }
        return new Range(80, 110);
    }

    /** Total sleep across 24 hours including naps, in minutes, by age. */
    public static Range sleepMinutes(int ageMonths) {
        if (ageMonths < 4) {
            return new Range(14 * 60, 17 * 60);
        }
        return new Range(12 * 60, 16 * 60);
    }

    /** The intake band for a weight, capped where the per-kg figure stops applying. */
    public static Range intakeMl(double weightKg, int ageMonths) {
        Range perKg = intakeMlPerKg(ageMonths);
        return new Range(
                Math.min((int) Math.round(perKg.low() * weightKg), MAX_INTAKE_ML_PER_DAY),
                Math.min((int) Math.round(perKg.high() * weightKg), MAX_INTAKE_ML_PER_DAY));
    }
}
