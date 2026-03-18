package com.wex.payments.controller;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.wex.payments.constants.ApiConstants;
import com.wex.payments.constants.ValidationConstants;
import com.wex.payments.dto.ApiErrorResponse;
import com.wex.payments.dto.CreatePurchaseTransactionRequest;
import com.wex.payments.exception.CurrencyConversionNotAvailableException;
import com.wex.payments.exception.PurchaseNotFoundException;
import com.wex.payments.exception.UpstreamExchangeRateException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private Validator validator;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.afterPropertiesSet();
        validator = factoryBean;
    }

    @Test
    void handlesMethodArgumentValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "description", ValidationConstants.DESCRIPTION_MAX_LENGTH_MESSAGE));

        Method method = TestController.class.getDeclaredMethod("create", CreatePurchaseTransactionRequest.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ApiConstants.VALIDATION_FAILED_MESSAGE);
        assertThat(response.getBody().details()).containsExactly("description: " + ValidationConstants.DESCRIPTION_MAX_LENGTH_MESSAGE);
    }

    @Test
    void handlesConstraintViolations() {
        CreatePurchaseTransactionRequest request = new CreatePurchaseTransactionRequest(
                "",
                null,
                BigDecimal.ZERO
        );
        ConstraintViolationException exception = new ConstraintViolationException(validator.validate(request));

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ApiConstants.VALIDATION_FAILED_MESSAGE);
        assertThat(response.getBody().details()).anyMatch(detail -> detail.contains("description"));
        assertThat(response.getBody().details()).anyMatch(detail -> detail.contains("transactionDate"));
        assertThat(response.getBody().details()).anyMatch(detail -> detail.contains("purchaseAmount"));
    }

    @Test
    void handlesUnreadableMessageForInvalidTransactionDateField() {
        InvalidFormatException cause = new InvalidFormatException(null, "bad date", "03/10/2026", LocalDate.class);
        cause.prependPath(new Object(), "transactionDate");
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("bad request", cause, null);

        ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableMessage(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ApiConstants.VALIDATION_FAILED_MESSAGE);
        assertThat(response.getBody().details()).containsExactly(ValidationConstants.TRANSACTION_DATE_INVALID_FORMAT_DETAIL);
    }

    @Test
    void handlesUnreadableMessageForLocalDateParseFailure() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "bad request",
                new DateTimeParseException("Text 'bad' could not be parsed", "bad", 0),
                null
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableMessage(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ApiConstants.VALIDATION_FAILED_MESSAGE);
        assertThat(response.getBody().details()).containsExactly(ValidationConstants.TRANSACTION_DATE_INVALID_FORMAT_DETAIL);
    }

    @Test
    void handlesUnreadableMessageForGenericPayloadErrors() {
        IllegalStateException cause = new IllegalStateException("payload malformed");
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("bad request", cause, null);

        ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableMessage(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ApiConstants.REQUEST_BODY_PARSE_FAILED_MESSAGE);
        assertThat(response.getBody().details()).containsExactly("payload malformed");
    }

    @Test
    void handlesPurchaseNotFound() {
        UUID purchaseId = UUID.randomUUID();

        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(new PurchaseNotFoundException(purchaseId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains(purchaseId.toString());
    }

    @Test
    void handlesConversionUnavailable() {
        ResponseEntity<ApiErrorResponse> response = handler.handleConversionUnavailable(
                new CurrencyConversionNotAvailableException("Canada-Dollar", LocalDate.of(2026, 2, 10))
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("cannot be converted");
    }

    @Test
    void handlesUpstreamFailure() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUpstreamFailure(
                new UpstreamExchangeRateException("Treasury unavailable")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Treasury unavailable");
    }

    @Test
    void handlesIllegalArgumentAsValidationFailure() {
        ResponseEntity<ApiErrorResponse> response = handler.handleIllegalArgument(new IllegalArgumentException("must be on or after 2001-03-31"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ApiConstants.VALIDATION_FAILED_MESSAGE);
        assertThat(response.getBody().details()).containsExactly("transactionDate: must be on or after 2001-03-31");
    }

    private static final class TestController {
        @SuppressWarnings("unused")
        private void create(CreatePurchaseTransactionRequest request) {
        }
    }
}
