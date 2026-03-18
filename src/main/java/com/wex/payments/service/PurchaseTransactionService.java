package com.wex.payments.service;

import com.wex.payments.domain.PurchaseTransaction;
import com.wex.payments.constants.TreasuryConstants;
import com.wex.payments.dto.ConvertedPurchaseTransactionResponse;
import com.wex.payments.dto.CreatePurchaseTransactionRequest;
import com.wex.payments.dto.PurchaseTransactionResponse;
import com.wex.payments.exception.CurrencyConversionNotAvailableException;
import com.wex.payments.exception.PurchaseNotFoundException;
import com.wex.payments.repository.PurchaseTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class PurchaseTransactionService {

    private final PurchaseTransactionRepository repository;
    private final ExchangeRateClient exchangeRateClient;

    public PurchaseTransactionService(PurchaseTransactionRepository repository, ExchangeRateClient exchangeRateClient) {
        this.repository = repository;
        this.exchangeRateClient = exchangeRateClient;
    }

    @Transactional
    public PurchaseTransactionResponse createPurchase(CreatePurchaseTransactionRequest request) {
        if (request.transactionDate().isBefore(CreatePurchaseTransactionRequest.MIN_SUPPORTED_TREASURY_DATE)) {
            throw new IllegalArgumentException(TreasuryConstants.MIN_SUPPORTED_DATE_MESSAGE);
        }

        PurchaseTransaction transaction = new PurchaseTransaction(
                request.description().trim(),
                request.transactionDate(),
                request.purchaseAmount().setScale(2, RoundingMode.HALF_UP)
        );

        PurchaseTransaction savedTransaction = repository.save(transaction);
        return new PurchaseTransactionResponse(
                savedTransaction.getId(),
                savedTransaction.getDescription(),
                savedTransaction.getTransactionDate(),
                savedTransaction.getPurchaseAmountUsd()
        );
    }

    @Transactional(readOnly = true)
    public ConvertedPurchaseTransactionResponse getConvertedPurchase(UUID purchaseId, String countryCurrency) {
        PurchaseTransaction transaction = repository.findById(purchaseId)
                .orElseThrow(() -> new PurchaseNotFoundException(purchaseId));

        ExchangeRateQuote quote = exchangeRateClient.findExchangeRate(countryCurrency, transaction.getTransactionDate())
                .orElseThrow(() -> new CurrencyConversionNotAvailableException(countryCurrency, transaction.getTransactionDate()));

        BigDecimal convertedAmount = transaction.getPurchaseAmountUsd()
                .multiply(quote.exchangeRate())
                .setScale(2, RoundingMode.HALF_UP);

        return new ConvertedPurchaseTransactionResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getPurchaseAmountUsd(),
                quote.countryCurrency(),
                quote.exchangeRateDate(),
                quote.exchangeRate(),
                convertedAmount
        );
    }
}
