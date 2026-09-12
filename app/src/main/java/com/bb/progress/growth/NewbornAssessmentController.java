package com.bb.progress.growth;

import com.bb.progress.baby.Baby;
import com.bb.progress.baby.BabyService;
import com.bb.progress.common.ApiException;
import com.bb.progress.growth.NewbornSizeService.Assessment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the baby's size at birth sat on the INTERGROWTH-21st newborn standards. Separate from
 * the WHO growth curves because it answers a different question against a different x-axis:
 * size relative to gestational age, not growth since birth.
 */
@RestController
public class NewbornAssessmentController {

    public record NewbornAssessmentResponse(
            int gestationalAgeDays,
            String standard,
            boolean covered,
            List<Assessment> assessments) {
    }

    private final BabyService babyService;
    private final NewbornSizeService newbornSizeService;

    public NewbornAssessmentController(BabyService babyService, NewbornSizeService newbornSizeService) {
        this.babyService = babyService;
        this.newbornSizeService = newbornSizeService;
    }

    @GetMapping("/api/newborn-assessment")
    public NewbornAssessmentResponse assess() {
        Baby baby = babyService.get();
        Integer gestationalAgeDays = baby.getGestationalAgeDays();
        if (gestationalAgeDays == null) {
            throw ApiException.notFound("GESTATIONAL_AGE_NOT_SET",
                    "Gestational age at birth has not been recorded");
        }
        GrowthRecord birth = babyService.getBirthRecord();
        if (birth == null) {
            throw ApiException.notFound("BIRTH_MEASUREMENTS_NOT_SET",
                    "No birth measurements have been recorded");
        }
        if (!NewbornSizeService.covers(gestationalAgeDays)) {
            // Below 33+0 weeks INTERGROWTH-21st publishes a separate Very Preterm standard,
            // which this app does not bundle — say so rather than extrapolate.
            return new NewbornAssessmentResponse(gestationalAgeDays, NewbornSizeService.STANDARD,
                    false, List.of());
        }

        List<Assessment> assessments = new ArrayList<>();
        add(assessments, GrowthMeasure.WEIGHT, birth.getWeightKg(), baby, gestationalAgeDays);
        add(assessments, GrowthMeasure.HEIGHT, birth.getHeightCm(), baby, gestationalAgeDays);
        add(assessments, GrowthMeasure.HEAD_CIRCUMFERENCE, birth.getHeadCircumferenceCm(), baby,
                gestationalAgeDays);
        return new NewbornAssessmentResponse(gestationalAgeDays, NewbornSizeService.STANDARD,
                true, assessments);
    }

    private void add(List<Assessment> into, GrowthMeasure measure, BigDecimal value, Baby baby,
            int gestationalAgeDays) {
        if (value != null) {
            into.add(newbornSizeService.assess(measure, baby.getGender(), gestationalAgeDays,
                    value.doubleValue()));
        }
    }
}
