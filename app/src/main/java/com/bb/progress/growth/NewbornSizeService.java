package com.bb.progress.growth;

import com.bb.progress.common.Gender;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Where a baby's size at birth sits on the INTERGROWTH-21st Newborn Size Standards
 * (Villar et al., Lancet 2014), which are indexed by gestational age rather than by age
 * since birth — the question the WHO postnatal curves cannot answer.
 *
 * <p>The published model is a two-piece skew-t with location mu, scale sigma, skew nu and
 * tau degrees of freedom for each gestational day and sex. Implementing its CDF reproduces
 * the project's published centile tables exactly (see NewbornSizeServiceTest).
 *
 * <p>Coverage is 33+0 to 42+6 weeks. Below that INTERGROWTH-21st publishes a separate Very
 * Preterm standard, which is deliberately not bundled rather than silently blended in here.
 */
@Service
public class NewbornSizeService {

    public static final int MIN_GESTATIONAL_DAYS = 231; // 33+0
    public static final int MAX_GESTATIONAL_DAYS = 300; // 42+6
    public static final String STANDARD = "INTERGROWTH-21st Newborn Size Standards";

    /** Skew-t parameters for one gestational day. */
    public record SkewTPoint(int gestDays, double mu, double sigma, double nu, double tau) {
    }

    public record Assessment(GrowthMeasure measure, double value, double centile, double zScore) {
    }

    private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution();

    private final ObjectMapper objectMapper;
    private final Map<GrowthMeasure, Map<Gender, Map<Integer, SkewTPoint>>> tables =
            new EnumMap<>(GrowthMeasure.class);

    public NewbornSizeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadTables() {
        for (GrowthMeasure measure : GrowthMeasure.values()) {
            Map<Gender, Map<Integer, SkewTPoint>> byGender = new EnumMap<>(Gender.class);
            for (Gender gender : Gender.values()) {
                Map<Integer, SkewTPoint> byDay = new HashMap<>();
                for (SkewTPoint point : loadResource(resourceName(measure, gender))) {
                    byDay.put(point.gestDays(), point);
                }
                byGender.put(gender, byDay);
            }
            tables.put(measure, byGender);
        }
    }

    private String resourceName(GrowthMeasure measure, Gender gender) {
        String measurePart = switch (measure) {
            case WEIGHT -> "weight";
            case HEIGHT -> "length";
            case HEAD_CIRCUMFERENCE -> "head-circumference";
        };
        return "/ig21/" + measurePart + "-" + (gender == Gender.MALE ? "boys" : "girls") + ".json";
    }

    private List<SkewTPoint> loadResource(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing INTERGROWTH-21st reference data: " + path);
            }
            return List.of(objectMapper.readValue(in, SkewTPoint[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load INTERGROWTH-21st data: " + path, e);
        }
    }

    public static boolean covers(int gestationalAgeDays) {
        return gestationalAgeDays >= MIN_GESTATIONAL_DAYS && gestationalAgeDays <= MAX_GESTATIONAL_DAYS;
    }

    public Assessment assess(GrowthMeasure measure, Gender gender, int gestationalAgeDays, double value) {
        SkewTPoint point = tables.get(measure).get(gender).get(gestationalAgeDays);
        if (point == null) {
            throw new IllegalArgumentException("Gestational age outside the standard: " + gestationalAgeDays);
        }
        double centile = cumulativeProbability(value, point) * 100.0;
        // Clamp before inverting: a value far into a tail would otherwise give infinity.
        double bounded = Math.min(Math.max(centile / 100.0, 1e-6), 1 - 1e-6);
        double zScore = STANDARD_NORMAL.inverseCumulativeProbability(bounded);
        return new Assessment(measure, value, round(centile, 1), round(zScore, 2));
    }

    /** The measurement at a given centile — the inverse of {@link #assess}. */
    public double valueAtCentile(GrowthMeasure measure, Gender gender, int gestationalAgeDays,
            double centile) {
        SkewTPoint point = tables.get(measure).get(gender).get(gestationalAgeDays);
        if (point == null) {
            throw new IllegalArgumentException("Gestational age outside the standard: " + gestationalAgeDays);
        }
        return quantile(centile / 100.0, point);
    }

    /** CDF of the two-piece skew-t used by the standard. */
    double cumulativeProbability(double y, SkewTPoint p) {
        TDistribution t = new TDistribution(p.tau());
        double z = (y - p.mu()) / p.sigma();
        double nuSquared = p.nu() * p.nu();
        if (y < p.mu()) {
            return 2.0 / (1.0 + nuSquared) * t.cumulativeProbability(p.nu() * z);
        }
        return 1.0 - (2.0 * nuSquared / (1.0 + nuSquared)) * (1.0 - t.cumulativeProbability(z / p.nu()));
    }

    /** Inverse of {@link #cumulativeProbability}, obtained by inverting each piece in turn. */
    double quantile(double probability, SkewTPoint p) {
        TDistribution t = new TDistribution(p.tau());
        double nuSquared = p.nu() * p.nu();
        double joinProbability = 1.0 / (1.0 + nuSquared); // the CDF value at y == mu
        double z;
        if (probability < joinProbability) {
            z = t.inverseCumulativeProbability(probability * (1.0 + nuSquared) / 2.0) / p.nu();
        } else {
            z = p.nu() * t.inverseCumulativeProbability(
                    1.0 - (1.0 - probability) * (1.0 + nuSquared) / (2.0 * nuSquared));
        }
        return p.mu() + p.sigma() * z;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
