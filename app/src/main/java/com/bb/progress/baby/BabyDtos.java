package com.bb.progress.baby;

import com.bb.progress.common.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class BabyDtos {

    private BabyDtos() {
    }

    public record BabyRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @PastOrPresent LocalDate dateOfBirth,
            @NotNull Gender gender) {
    }

    public record BabyResponse(
            String name,
            LocalDate dateOfBirth,
            Gender gender,
            boolean hasPhoto) {

        public static BabyResponse from(Baby baby) {
            return new BabyResponse(baby.getName(), baby.getDateOfBirth(), baby.getGender(),
                    baby.getPhotoPath() != null);
        }
    }
}
