package com.bb.progress.growth;

import com.bb.progress.common.Gender;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Serves WHO Child Growth Standards percentile curves, computed from the WHO LMS
 * reference tables bundled under resources/who/ (one file per measure and gender,
 * monthly values from birth to 36 months).
 *
 * Percentile value at a given age: M * (1 + L*S*z)^(1/L), where z is the standard
 * normal deviate of the percentile.
 */
@Service
public class WhoPercentileService {

    public record LmsPoint(int ageMonths, double l, double m, double s) {
    }

    public record CurvePoint(int ageMonths, double value) {
    }

    public record StandardsResponse(GrowthMeasure measure, Gender gender,
            Map<String, List<CurvePoint>> curves) {
    }

    private static final Map<String, Double> Z_BY_PERCENTILE = new LinkedHashMap<>();

    static {
        Z_BY_PERCENTILE.put("p3", -1.8807936081512509);
        Z_BY_PERCENTILE.put("p15", -1.0364333894937898);
        Z_BY_PERCENTILE.put("p50", 0.0);
        Z_BY_PERCENTILE.put("p85", 1.0364333894937898);
        Z_BY_PERCENTILE.put("p97", 1.8807936081512509);
    }

    private final ObjectMapper objectMapper;
    private final Map<GrowthMeasure, Map<Gender, List<LmsPoint>>> tables = new EnumMap<>(GrowthMeasure.class);

    public WhoPercentileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadTables() {
        for (GrowthMeasure measure : GrowthMeasure.values()) {
            Map<Gender, List<LmsPoint>> byGender = new EnumMap<>(Gender.class);
            for (Gender gender : Gender.values()) {
                byGender.put(gender, loadResource(resourceName(measure, gender)));
            }
            tables.put(measure, byGender);
        }
    }

    private String resourceName(GrowthMeasure measure, Gender gender) {
        String measurePart = switch (measure) {
            case WEIGHT -> "weight-for-age";
            case HEIGHT -> "height-for-age";
            case HEAD_CIRCUMFERENCE -> "head-circumference-for-age";
        };
        String genderPart = gender == Gender.MALE ? "boys" : "girls";
        return "/who/" + measurePart + "-" + genderPart + ".json";
    }

    private List<LmsPoint> loadResource(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing WHO reference data: " + path);
            }
            LmsPoint[] points = objectMapper.readValue(in, LmsPoint[].class);
            return List.of(points);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load WHO reference data: " + path, e);
        }
    }

    public StandardsResponse standards(GrowthMeasure measure, Gender gender) {
        List<LmsPoint> lms = tables.get(measure).get(gender);
        Map<String, List<CurvePoint>> curves = new LinkedHashMap<>();
        Z_BY_PERCENTILE.forEach((name, z) -> curves.put(name, lms.stream()
                .map(p -> new CurvePoint(p.ageMonths(), round1(valueAt(p, z))))
                .toList()));
        return new StandardsResponse(measure, gender, curves);
    }

    /** LMS formula; L == 0 degenerates to M * exp(S*z). */
    double valueAt(LmsPoint p, double z) {
        if (p.l() == 0) {
            return p.m() * Math.exp(p.s() * z);
        }
        return p.m() * Math.pow(1 + p.l() * p.s() * z, 1 / p.l());
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
