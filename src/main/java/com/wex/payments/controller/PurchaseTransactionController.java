package com.wex.payments.controller;

import com.wex.payments.constants.ApiConstants;
import com.wex.payments.constants.ValidationConstants;
import com.wex.payments.dto.ConvertedPurchaseTransactionResponse;
import com.wex.payments.dto.CreatePurchaseTransactionRequest;
import com.wex.payments.dto.PurchaseTransactionResponse;
import com.wex.payments.exception.InvalidCountryCurrencyException;
import com.wex.payments.service.PurchaseTransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping(ApiConstants.PURCHASES_BASE_PATH)
public class PurchaseTransactionController {

    private final PurchaseTransactionService purchaseTransactionService;

    public PurchaseTransactionController(PurchaseTransactionService purchaseTransactionService) {
        this.purchaseTransactionService = purchaseTransactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseTransactionResponse createPurchase(@Valid @RequestBody CreatePurchaseTransactionRequest request) {
        return purchaseTransactionService.createPurchase(request);
    }

    @GetMapping("/{purchaseId}")
    public ConvertedPurchaseTransactionResponse getPurchaseInTargetCurrency(
            @PathVariable UUID purchaseId,
            @RequestParam(ApiConstants.COUNTRY_CURRENCY_PARAM)
            @NotBlank(message = ValidationConstants.COUNTRY_CURRENCY_REQUIRED_MESSAGE)
            @Pattern(regexp = ValidationConstants.COUNTRY_CURRENCY_ALLOWED_PATTERN, message = ValidationConstants.COUNTRY_CURRENCY_INVALID_MESSAGE)
            String countryCurrency) {
        String trimmedCountryCurrency = trimCountryCurrency(countryCurrency);
        if (!trimmedCountryCurrency.matches(ValidationConstants.COUNTRY_CURRENCY_ALLOWED_PATTERN)) {
            throw new InvalidCountryCurrencyException();
        }
        return purchaseTransactionService.getConvertedPurchase(purchaseId, trimmedCountryCurrency);
    }

    private String trimCountryCurrency(String countryCurrency) {
        return countryCurrency.trim();
    }
}
