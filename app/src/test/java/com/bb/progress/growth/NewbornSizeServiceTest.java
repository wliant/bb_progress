package com.bb.progress.growth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.bb.progress.common.Gender;
import com.bb.progress.growth.NewbornSizeService.Assessment;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

/**
 * Golden-value tests against the INTERGROWTH-21st project's own published centile tables:
 * feeding in the value the project prints for a centile must give that centile back. This
 * pins both the skew-t implementation and the bundled parameters.
 */
class NewbornSizeServiceTest {

    private static NewbornSizeService service;

    @BeforeAll
    static void setUp() {
        service = new NewbornSizeService(new ObjectMapper());
        service.loadTables();
    }

    /** Published values at the 3rd, 10th, 50th, 90th and 97th centiles for a gestational day. */
    record Golden(GrowthMeasure measure, Gender gender, int gestDays,
            double p3, double p10, double p50, double p90, double p97) {
    }

    static Stream<Arguments> published() {
        return Stream.of(
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 231, 1.18, 1.43, 1.95, 2.52, 2.82),
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 252, 1.93, 2.18, 2.69, 3.25, 3.54),
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 266, 2.32, 2.57, 3.07, 3.63, 3.92),
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 280, 2.63, 2.88, 3.38, 3.94, 4.22),
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 294, 2.88, 3.12, 3.62, 4.17, 4.46),
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 300, 2.96, 3.21, 3.71, 4.25, 4.54),
                new Golden(GrowthMeasure.WEIGHT, Gender.FEMALE, 231, 1.20, 1.41, 1.86, 2.35, 2.61),
                new Golden(GrowthMeasure.WEIGHT, Gender.FEMALE, 252, 1.92, 2.14, 2.60, 3.12, 3.39),
                new Golden(GrowthMeasure.WEIGHT, Gender.FEMALE, 266, 2.28, 2.50, 2.97, 3.51, 3.78),
                new Golden(GrowthMeasure.WEIGHT, Gender.FEMALE, 280, 2.55, 2.78, 3.26, 3.80, 4.08),
                new Golden(GrowthMeasure.WEIGHT, Gender.FEMALE, 294, 2.74, 2.98, 3.46, 4.01, 4.30),
                new Golden(GrowthMeasure.WEIGHT, Gender.FEMALE, 300, 2.80, 3.04, 3.53, 4.08, 4.37),
                new Golden(GrowthMeasure.HEIGHT, Gender.MALE, 231, 39.7, 41.1, 43.8, 46.6, 48.0),
                new Golden(GrowthMeasure.HEIGHT, Gender.MALE, 266, 45.2, 46.4, 48.6, 50.8, 52.0),
                new Golden(GrowthMeasure.HEIGHT, Gender.MALE, 280, 46.8, 47.8, 49.9, 52.0, 53.1),
                new Golden(GrowthMeasure.HEIGHT, Gender.MALE, 300, 48.5, 49.5, 51.4, 53.4, 54.4),
                new Golden(GrowthMeasure.HEIGHT, Gender.FEMALE, 231, 39.8, 41.0, 43.4, 45.7, 46.9),
                new Golden(GrowthMeasure.HEIGHT, Gender.FEMALE, 266, 44.8, 45.9, 48.0, 50.1, 51.1),
                new Golden(GrowthMeasure.HEIGHT, Gender.FEMALE, 280, 46.1, 47.2, 49.2, 51.2, 52.2),
                new Golden(GrowthMeasure.HEIGHT, Gender.FEMALE, 300, 47.6, 48.6, 50.6, 52.5, 53.5),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.MALE, 231, 28.2, 29.1, 30.9, 32.7, 33.6),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.MALE, 266, 31.2, 32.0, 33.5, 35.0, 35.8),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.MALE, 280, 32.2, 32.9, 34.3, 35.8, 36.6),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.MALE, 300, 33.3, 34.0, 35.4, 36.8, 37.5),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.FEMALE, 231, 27.9, 28.8, 30.5, 32.2, 33.1),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.FEMALE, 266, 30.9, 31.6, 33.0, 34.5, 35.3),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.FEMALE, 280, 31.7, 32.4, 33.8, 35.2, 35.9),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.FEMALE, 300, 32.7, 33.3, 34.6, 36.0, 36.6))
                .map(Arguments::of);
    }

    /**
     * Compared on the measurement rather than the centile, because near the median a rounded
     * centile moves by over a whole point for one unit in the last published decimal.
     *
     * <p>Tolerances are the agreement actually observed against every published centile at every
     * gestational day in range: at worst 15 g on weight and 0.055 cm on the lengths, which comes
     * from the rounding in the published parameter workbook. A genuine error in the distribution
     * or the data would miss by far more than this.
     */
    @ParameterizedTest
    @MethodSource("published")
    void reproducesThePublishedCentileTables(Golden golden) {
        double tolerance = golden.measure() == GrowthMeasure.WEIGHT ? 0.02 : 0.06;
        assertThat(valueAt(golden, 3)).isCloseTo(golden.p3(), within(tolerance));
        assertThat(valueAt(golden, 10)).isCloseTo(golden.p10(), within(tolerance));
        assertThat(valueAt(golden, 50)).isCloseTo(golden.p50(), within(tolerance));
        assertThat(valueAt(golden, 90)).isCloseTo(golden.p90(), within(tolerance));
        assertThat(valueAt(golden, 97)).isCloseTo(golden.p97(), within(tolerance));
    }

    /** The centile and its inverse must agree, so a reported centile can be trusted both ways. */
    @ParameterizedTest
    @MethodSource("published")
    void assessAndValueAtCentileAreInverses(Golden golden) {
        for (double centile : new double[] {1, 3, 10, 25, 50, 75, 90, 97, 99}) {
            double value = valueAt(golden, centile);
            assertThat(centileOf(golden, value)).isCloseTo(centile, within(0.1));
        }
    }

    @ParameterizedTest
    @MethodSource("published")
    void zScoresMatchTheCentilesTheyCorrespondTo(Golden golden) {
        assertThat(assess(golden, valueAt(golden, 50)).zScore()).isCloseTo(0.0, within(0.01));
        assertThat(assess(golden, valueAt(golden, 3)).zScore()).isCloseTo(-1.881, within(0.01));
        assertThat(assess(golden, valueAt(golden, 97)).zScore()).isCloseTo(1.881, within(0.01));
    }

    @Test
    void centilesRiseWithTheMeasurement() {
        double previous = -1;
        for (double weight = 2.0; weight <= 5.0; weight += 0.1) {
            double centile = service.assess(GrowthMeasure.WEIGHT, Gender.MALE, 280, weight).centile();
            assertThat(centile).isGreaterThanOrEqualTo(previous);
            previous = centile;
        }
        assertThat(service.assess(GrowthMeasure.WEIGHT, Gender.MALE, 280, 2.0).centile())
                .isLessThan(service.assess(GrowthMeasure.WEIGHT, Gender.MALE, 280, 5.0).centile());
    }

    @Test
    void coversOnlyTheStandardsPublishedRange() {
        assertThat(NewbornSizeService.covers(231)).isTrue();  // 33+0
        assertThat(NewbornSizeService.covers(300)).isTrue();  // 42+6
        assertThat(NewbornSizeService.covers(230)).isFalse(); // very preterm: separate standard
        assertThat(NewbornSizeService.covers(301)).isFalse();

        assertThatThrownBy(() -> service.assess(GrowthMeasure.WEIGHT, Gender.MALE, 230, 3.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyGestationalDayInRangeIsAvailableForBothSexes() {
        for (GrowthMeasure measure : GrowthMeasure.values()) {
            for (Gender gender : Gender.values()) {
                for (int day = 231; day <= 300; day++) {
                    Assessment assessment = service.assess(measure, gender, day, 3.0);
                    assertThat(assessment.centile()).isBetween(0.0, 100.0);
                }
            }
        }
    }

    /** A boy of average weight born at term should land near the middle. */
    @Test
    void aTypicalTermBoySitsNearTheMedian() {
        Assessment assessment = service.assess(GrowthMeasure.WEIGHT, Gender.MALE, 280, 3.38);
        assertThat(assessment.centile()).isCloseTo(50.0, within(1.0));
        assertThat(assessment.zScore()).isCloseTo(0.0, within(0.05));
    }

    private static Assessment assess(Golden golden, double value) {
        return service.assess(golden.measure(), golden.gender(), golden.gestDays(), value);
    }

    private static double centileOf(Golden golden, double value) {
        return assess(golden, value).centile();
    }

    private static double valueAt(Golden golden, double centile) {
        return service.valueAtCentile(golden.measure(), golden.gender(), golden.gestDays(), centile);
    }

    @Test
    void sanityCheckAllPublishedRowsAreCovered() {
        List<Arguments> rows = published().toList();
        assertThat(rows).hasSize(28);
    }
}
