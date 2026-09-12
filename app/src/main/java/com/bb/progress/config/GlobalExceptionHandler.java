package com.bb.progress.config;

import com.bb.progress.common.ApiError;
import com.bb.progress.common.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API in the same shape, always carrying a stable {@code code}
 * the frontend maps to a localized message. Extending {@link ResponseEntityExceptionHandler}
 * means Spring's own MVC exceptions (bad enum, unparseable body, missing part, wrong
 * method/media type) are rewritten into that shape too instead of falling through to
 * Spring Boot's default error body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getStatus().value(), ex.getCode(), ex.getMessage()));
    }

    /**
     * A unique constraint tripped by concurrent writes — e.g. two taps on Save racing to
     * insert the same measurement date. The database keeps the data correct; this turns the
     * resulting failure into the same error the single-request path already returns.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        String detail = rootMessage(ex).toLowerCase();
        ApiError error;
        if (detail.contains("measured_on")) {
            error = ApiError.of(400, "DUPLICATE_DATE", "A record for this date already exists");
        } else if (detail.contains("milestone_id")) {
            error = ApiError.of(409, "ACHIEVEMENT_CONFLICT", "This milestone was updated concurrently");
        } else {
            log.warn("Unmapped data integrity violation", ex);
            error = ApiError.of(409, "DATA_CONFLICT", "The change conflicts with existing data");
        }
        return ResponseEntity.status(error.status()).body(error);
    }

    /** Last resort: an unexpected failure still answers with the documented shape. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_ERROR", "Unexpected server error"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ApiError error = switch (ex) {
            case MethodArgumentNotValidException e -> ApiError.of(400, "VALIDATION_ERROR",
                    "Validation failed", fieldErrors(e));
            case MaxUploadSizeExceededException e -> ApiError.of(413, "FILE_TOO_LARGE",
                    "Uploaded file exceeds the size limit");
            case MethodArgumentTypeMismatchException e -> ApiError.of(400, "INVALID_PARAMETER",
                    "Invalid value for parameter '" + e.getName() + "'");
            case MissingServletRequestParameterException e -> ApiError.of(400, "MISSING_PARAMETER",
                    "Missing required parameter '" + e.getParameterName() + "'");
            case MissingServletRequestPartException e -> ApiError.of(400, "MISSING_FILE",
                    "Missing file part '" + e.getRequestPartName() + "'");
            case HttpMessageNotReadableException e -> ApiError.of(400, "MALFORMED_REQUEST",
                    "Request body could not be parsed");
            default -> ApiError.of(status.value(), codeFor(status), status.getReasonPhrase());
        };
        return ResponseEntity.status(status).headers(headers).body(error);
    }

    private static List<ApiError.FieldError> fieldErrors(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getCode(), f.getDefaultMessage()))
                .toList();
    }

    private static String codeFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            case PAYLOAD_TOO_LARGE -> "FILE_TOO_LARGE";
            default -> status.is4xxClientError() ? "BAD_REQUEST" : "INTERNAL_ERROR";
        };
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "" : cause.getMessage();
    }
}
