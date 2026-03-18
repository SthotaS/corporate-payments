package com.wex.payments.service;

import com.wex.payments.domain.PurchaseTransaction;
import com.wex.payments.constants.TreasuryConstants;
import com.wex.payments.dto.ConvertedPurchaseTransactionResponse;
import com.wex.payments.dto.CreatePurchaseTransactionRequest;
import com.wex.payments.dto.PurchaseTransactionResponse;
import com.wex.payments.exception.CurrencyConversionNotAvailableException;
import com.wex.payments.exception.PurchaseNotFoundException;
import com.wex.payments.logging.LoggingConstants;
import com.wex.payments.repository.PurchaseTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class PurchaseTransactionService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseTransactionService.class);

    private final PurchaseTransactionRepository repository;
    private final ExchangeRateClient exchangeRateClient;

    public PurchaseTransactionService(PurchaseTransactionRepository repository, ExchangeRateClient exchangeRateClient) {
        this.repository = repository;
        this.exchangeRateClient = exchangeRateClient;
    }

    @Transactional
    public PurchaseTransactionResponse createPurchase(CreatePurchaseTransactionRequest request) {
        log.info("creating purchase description='{}' transactionDate={} purchaseAmount={}",
                request.description(),
                request.transactionDate(),
                request.purchaseAmount());

        if (request.transactionDate().isBefore(CreatePurchaseTransactionRequest.MIN_SUPPORTED_TREASURY_DATE)) {
            log.warn("purchase rejected because transactionDate={} is before supported treasury date {}",
                    request.transactionDate(),
                    CreatePurchaseTransactionRequest.MIN_SUPPORTED_TREASURY_DATE);
            throw new IllegalArgumentException(TreasuryConstants.MIN_SUPPORTED_DATE_MESSAGE);
        }

        PurchaseTransaction transaction = new PurchaseTransaction(
                request.description().trim(),
                request.transactionDate(),
                request.purchaseAmount().setScale(2, RoundingMode.HALF_UP)
        );

        log.debug("normalized purchase payload originalDescription='{}' trimmedDescription='{}' originalAmount={} normalizedAmount={}",
                request.description(),
                transaction.getDescription(),
                request.purchaseAmount(),
                transaction.getPurchaseAmountUsd());

        PurchaseTransaction savedTransaction = repository.save(transaction);
        MDC.put(LoggingConstants.PURCHASE_ID, savedTransaction.getId().toString());
        log.info("purchase stored successfully purchaseId={} normalizedAmount={}",
                savedTransaction.getId(),
                savedTransaction.getPurchaseAmountUsd());
        return new PurchaseTransactionResponse(
                savedTransaction.getId(),
                savedTransaction.getDescription(),
                savedTransaction.getTransactionDate(),
                savedTransaction.getPurchaseAmountUsd()
        );
    }

    @Transactional(readOnly = true)
    public ConvertedPurchaseTransactionResponse getConvertedPurchase(UUID purchaseId, String countryCurrency) {
        MDC.put(LoggingConstants.PURCHASE_ID, purchaseId.toString());
        log.info("retrieving converted purchase purchaseId={} targetCurrency={}", purchaseId, countryCurrency);

        PurchaseTransaction transaction = repository.findById(purchaseId)
                .orElseThrow(() -> new PurchaseNotFoundException(purchaseId));

        log.debug("loaded purchase purchaseId={} transactionDate={} amountUsd={} description='{}'",
                transaction.getId(),
                transaction.getTransactionDate(),
                transaction.getPurchaseAmountUsd(),
                transaction.getDescription());

        ExchangeRateQuote quote = exchangeRateClient.findExchangeRate(countryCurrency, transaction.getTransactionDate())
                .orElseThrow(() -> new CurrencyConversionNotAvailableException(countryCurrency, transaction.getTransactionDate()));

        BigDecimal convertedAmount = transaction.getPurchaseAmountUsd()
                .multiply(quote.exchangeRate())
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("conversion calculation purchaseId={} amountUsd={} exchangeRate={} convertedAmount={}",
                transaction.getId(),
                transaction.getPurchaseAmountUsd(),
                quote.exchangeRate(),
                convertedAmount);

        log.info("purchase conversion successful purchaseId={} exchangeRateDate={} exchangeRate={} convertedAmount={}",
                transaction.getId(),
                quote.exchangeRateDate(),
                quote.exchangeRate(),
                convertedAmount);

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
