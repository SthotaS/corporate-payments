package com.wex.payments.controller;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.wex.payments.constants.ApiConstants;
import com.wex.payments.constants.ValidationConstants;
import com.wex.payments.dto.ApiErrorResponse;
import com.wex.payments.exception.CurrencyConversionNotAvailableException;
import com.wex.payments.exception.PurchaseNotFoundException;
import com.wex.payments.exception.UpstreamExchangeRateException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();

        log.warn("request validation failed details={}", details);

        return buildResponse(HttpStatus.BAD_REQUEST, ApiConstants.VALIDATION_FAILED_MESSAGE, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();

        log.warn("constraint validation failed details={}", details);

        return buildResponse(HttpStatus.BAD_REQUEST, ApiConstants.VALIDATION_FAILED_MESSAGE, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        InvalidFormatException invalidFormatException = findCause(exception, InvalidFormatException.class);
        if (invalidFormatException != null && invalidFormatException.getPath() != null) {
            String fieldName = invalidFormatException.getPath().stream()
                    .findFirst()
                    .map(reference -> reference.getFieldName())
                    .orElse("");

            if ("transactionDate".equals(fieldName)) {
                log.warn("request body date format invalid field={}", fieldName);
                return buildResponse(HttpStatus.BAD_REQUEST, ApiConstants.VALIDATION_FAILED_MESSAGE, List.of(ValidationConstants.TRANSACTION_DATE_INVALID_FORMAT_DETAIL));
            }
        }

        DateTimeParseException dateTimeParseException = findCause(exception, DateTimeParseException.class);
        if (dateTimeParseException != null || cause.getMessage().contains("LocalDate")) {
            log.warn("request body contains invalid LocalDate payload");
            return buildResponse(HttpStatus.BAD_REQUEST, ApiConstants.VALIDATION_FAILED_MESSAGE, List.of(ValidationConstants.TRANSACTION_DATE_INVALID_FORMAT_DETAIL));
        }

        log.warn("request body parsing failed cause={}", cause.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ApiConstants.REQUEST_BODY_PARSE_FAILED_MESSAGE, List.of(cause.getMessage()));
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> targetType) {
        Throwable current = throwable;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return targetType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    @ExceptionHandler(PurchaseNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PurchaseNotFoundException exception) {
        log.warn("purchase lookup failed message={}", exception.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(CurrencyConversionNotAvailableException.class)
    public ResponseEntity<ApiErrorResponse> handleConversionUnavailable(CurrencyConversionNotAvailableException exception) {
        log.warn("purchase conversion unavailable message={}", exception.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), List.of());
    }

    @ExceptionHandler(UpstreamExchangeRateException.class)
    public ResponseEntity<ApiErrorResponse> handleUpstreamFailure(UpstreamExchangeRateException exception) {
        log.error("upstream treasury exchange rate failure", exception);
        return buildResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("illegal argument encountered message={}", exception.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ApiConstants.VALIDATION_FAILED_MESSAGE, List.of("transactionDate: " + exception.getMessage()));
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details
        ));
    }
}
