package com.bb.progress.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bb.progress.common.ApiError;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

/**
 * The concurrent-write path is hard to trigger deterministically end-to-end, so the
 * mapping from a constraint violation to the user-facing error is pinned here.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static DataIntegrityViolationException violation(String constraintDetail) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \""
                        + constraintDetail + "\""));
    }

    @Test
    void duplicateMeasurementDateMapsToTheSameErrorAsTheValidatedPath() {
        ResponseEntity<ApiError> response = handler.handleDataIntegrity(
                violation("growth_record_measured_on_key"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_DATE");
    }

    @Test
    void concurrentMilestoneWriteMapsToConflict() {
        ResponseEntity<ApiError> response = handler.handleDataIntegrity(
                violation("milestone_achievement_milestone_id_key"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("ACHIEVEMENT_CONFLICT");
    }

    @Test
    void unrecognisedConstraintStillAnswersInTheDocumentedShape() {
        ResponseEntity<ApiError> response = handler.handleDataIntegrity(
                violation("some_other_constraint"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().message()).isNotBlank();
    }

    @Test
    void unexpectedFailureCarriesACodeInsteadOfLeakingADefaultErrorBody() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("boom");
    }
}
