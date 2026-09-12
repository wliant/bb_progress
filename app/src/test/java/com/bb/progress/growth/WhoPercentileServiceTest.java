package com.bb.progress.growth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bb.progress.common.Gender;
import com.bb.progress.growth.WhoPercentileService.LmsPoint;
import com.bb.progress.growth.WhoPercentileService.StandardsResponse;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden-value tests: the LMS math and the bundled reference data must reproduce
 * WHO's own published SD values (SD2neg / median / SD2 columns of the official
 * z-score expanded tables) at sampled ages.
 */
class WhoPercentileServiceTest {

    private static final double Z2 = 2.0;
    private static WhoPercentileService service;

    @BeforeAll
    static void setUp() {
        service = new WhoPercentileService(new ObjectMapper());
        service.loadTables();
    }

    record Golden(GrowthMeasure measure, Gender gender, int ageMonths, double sd2neg, double median, double sd2) {
    }

    static Stream<Arguments> goldenValues() {
        return Stream.of(
                // weight-for-age boys
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 0, 2.459, 3.346, 4.419),
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 6, 6.357, 7.939, 9.855),
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 12, 7.741, 9.646, 11.983),
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 24, 9.67, 12.148, 15.272),
                new Golden(GrowthMeasure.WEIGHT, Gender.MALE, 36, 11.28, 14.344, 18.315),
                // weight-for-age girls
                new Golden(GrowthMeasure.WEIGHT, Gender.FEMALE, 0, 2.395, 3.232, 4.23),
                new Golden(GrowthMeasure.WEIGHT, Gender.FEMALE, 12, 7.041, 8.946, 11.506),
                new Golden(GrowthMeasure.WEIGHT, Gender.FEMALE, 36, 10.806, 13.852, 18.14),
                // height-for-age boys
                new Golden(GrowthMeasure.HEIGHT, Gender.MALE, 0, 46.098, 49.884, 53.67),
                new Golden(GrowthMeasure.HEIGHT, Gender.MALE, 12, 70.987, 75.739, 80.491),
                new Golden(GrowthMeasure.HEIGHT, Gender.MALE, 36, 88.675, 96.089, 103.503),
                // height-for-age girls
                new Golden(GrowthMeasure.HEIGHT, Gender.FEMALE, 6, 61.217, 65.751, 70.285),
                new Golden(GrowthMeasure.HEIGHT, Gender.FEMALE, 24, 79.95, 86.401, 92.851),
                // head-circumference boys
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.MALE, 6, 40.898, 43.339, 45.781),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.MALE, 24, 45.527, 48.249, 50.972),
                // head-circumference girls
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.FEMALE, 0, 31.51, 33.879, 36.247),
                new Golden(GrowthMeasure.HEAD_CIRCUMFERENCE, Gender.FEMALE, 36, 45.685, 48.511, 51.336))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("goldenValues")
    void lmsMathReproducesWhoPublishedSdValues(Golden golden) {
        LmsPoint point = lmsPointAt(golden.measure(), golden.gender(), golden.ageMonths());
        assertThat(service.valueAt(point, -Z2)).isCloseTo(golden.sd2neg(), within(0.005));
        assertThat(service.valueAt(point, 0)).isCloseTo(golden.median(), within(0.005));
        assertThat(service.valueAt(point, Z2)).isCloseTo(golden.sd2(), within(0.005));
    }

    @Test
    void standardsReturnFiveCurvesCoveringBirthTo36Months() {
        StandardsResponse response = service.standards(GrowthMeasure.WEIGHT, Gender.FEMALE);
        assertThat(response.curves()).containsOnlyKeys("p3", "p15", "p50", "p85", "p97");
        response.curves().forEach((name, points) -> {
            assertThat(points).hasSize(37);
            assertThat(points.getFirst().ageMonths()).isZero();
            assertThat(points.getLast().ageMonths()).isEqualTo(36);
        });
    }

    @Test
    void percentilesAreOrdered() {
        Map<String, List<WhoPercentileService.CurvePoint>> curves =
                service.standards(GrowthMeasure.HEIGHT, Gender.MALE).curves();
        for (int i = 0; i < curves.get("p3").size(); i++) {
            double p3 = curves.get("p3").get(i).value();
            double p50 = curves.get("p50").get(i).value();
            double p97 = curves.get("p97").get(i).value();
            assertThat(p3).isLessThan(p50);
            assertThat(p50).isLessThan(p97);
        }
    }

    private LmsPoint lmsPointAt(GrowthMeasure measure, Gender gender, int ageMonths) {
        try (var in = WhoPercentileService.class.getResourceAsStream(resourcePath(measure, gender))) {
            LmsPoint[] points = new ObjectMapper().readValue(in, LmsPoint[].class);
            return points[ageMonths];
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String resourcePath(GrowthMeasure measure, Gender gender) {
        String measurePart = switch (measure) {
            case WEIGHT -> "weight-for-age";
            case HEIGHT -> "height-for-age";
            case HEAD_CIRCUMFERENCE -> "head-circumference-for-age";
        };
        return "/who/" + measurePart + "-" + (gender == Gender.MALE ? "boys" : "girls") + ".json";
    }
}
