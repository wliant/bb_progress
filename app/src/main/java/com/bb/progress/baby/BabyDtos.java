package com.bb.progress.baby;

import com.bb.progress.common.Gender;
import com.bb.progress.growth.GrowthRecord;
import com.bb.progress.photo.PhotoResponses;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public final class BabyDtos {

    private BabyDtos() {
    }

    public record BabyRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @PastOrPresent LocalDate dateOfBirth,
            LocalTime timeOfBirth,
            @NotNull Gender gender,
            @DecimalMin("0.3") @DecimalMax("40") BigDecimal birthWeightKg,
            @DecimalMin("20") @DecimalMax("150") BigDecimal birthLengthCm,
            @DecimalMin("20") @DecimalMax("70") BigDecimal birthHeadCircumferenceCm) {

        public boolean hasBirthMeasurements() {
            return birthWeightKg != null || birthLengthCm != null || birthHeadCircumferenceCm != null;
        }
    }

    public record BabyResponse(
            String name,
            LocalDate dateOfBirth,
            LocalTime timeOfBirth,
            Gender gender,
            boolean hasPhoto,
            String photoVersion,
            BigDecimal birthWeightKg,
            BigDecimal birthLengthCm,
            BigDecimal birthHeadCircumferenceCm) {

        public static BabyResponse from(Baby baby, GrowthRecord birthRecord) {
            return new BabyResponse(
                    baby.getName(),
                    baby.getDateOfBirth(),
                    baby.getTimeOfBirth(),
                    baby.getGender(),
                    baby.getPhotoPath() != null,
                    PhotoResponses.versionOf(baby.getPhotoPath()),
                    birthRecord == null ? null : birthRecord.getWeightKg(),
                    birthRecord == null ? null : birthRecord.getHeightCm(),
                    birthRecord == null ? null : birthRecord.getHeadCircumferenceCm());
        }
    }
}
