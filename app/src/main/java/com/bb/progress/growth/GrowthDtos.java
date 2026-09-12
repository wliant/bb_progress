package com.bb.progress.growth;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class GrowthDtos {

    private GrowthDtos() {
    }

    public record GrowthRecordRequest(
            @NotNull @PastOrPresent LocalDate measuredOn,
            @DecimalMin("0.3") @DecimalMax("40") BigDecimal weightKg,
            @DecimalMin("20") @DecimalMax("150") BigDecimal heightCm,
            @DecimalMin("20") @DecimalMax("70") BigDecimal headCircumferenceCm,
            @Size(max = 500) String note) {

        public boolean hasAnyMeasurement() {
            return weightKg != null || heightCm != null || headCircumferenceCm != null;
        }
    }

    public record GrowthRecordResponse(
            UUID id,
            LocalDate measuredOn,
            BigDecimal weightKg,
            BigDecimal heightCm,
            BigDecimal headCircumferenceCm,
            String note,
            boolean birth) {

        public static GrowthRecordResponse from(GrowthRecord record) {
            return new GrowthRecordResponse(record.getId(), record.getMeasuredOn(), record.getWeightKg(),
                    record.getHeightCm(), record.getHeadCircumferenceCm(), record.getNote(), record.isBirth());
        }
    }
}
