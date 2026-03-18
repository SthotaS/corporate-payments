package com.wex.payments.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wex.payments.dto.ConvertedPurchaseTransactionResponse;
import com.wex.payments.dto.CreatePurchaseTransactionRequest;
import com.wex.payments.dto.PurchaseTransactionResponse;
import com.wex.payments.exception.CurrencyConversionNotAvailableException;
import com.wex.payments.service.PurchaseTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseTransactionControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private StubPurchaseTransactionService purchaseTransactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        purchaseTransactionService = new StubPurchaseTransactionService();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new PurchaseTransactionController(purchaseTransactionService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createPurchaseReturnsCreatedTransaction() throws Exception {
        UUID purchaseId = UUID.randomUUID();
        CreatePurchaseTransactionRequest request = new CreatePurchaseTransactionRequest(
                "Lunch",
                LocalDate.of(2026, 3, 10),
                new BigDecimal("12.34")
        );
        purchaseTransactionService.createResponse = new PurchaseTransactionResponse(
                purchaseId,
                "Lunch",
                LocalDate.of(2026, 3, 10),
                new BigDecimal("12.34")
        );

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(purchaseId.toString()))
                .andExpect(jsonPath("$.purchaseAmountUsd").value(12.34));
    }

    @Test
    void createPurchaseRejectsTooLongDescription() throws Exception {
        String description = "x".repeat(51);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "description": "%s",
                                  "transactionDate": "2026-03-10",
                                  "purchaseAmount": 12.34
                                }
                                """.formatted(description)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("description: must not exceed 50 characters"));
    }

    @Test
    void createPurchaseRejectsInvalidDateFormat() throws Exception {
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Hotel",
                                  "transactionDate": "03/10/2026",
                                  "purchaseAmount": 12.34
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("validation failed"))
                .andExpect(jsonPath("$.details[0]").value("transactionDate: must be a valid date format"));
    }

    @Test
    void createPurchaseRejectsFutureDate() throws Exception {
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "description": "Hotel",
                                  "transactionDate": "2027-03-10",
                                  "purchaseAmount": 12.34
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("validation failed"))
                .andExpect(jsonPath("$.details[0]").value("transactionDate: transactionDate must not be in the future"));
    }

    @Test
    void createPurchaseRejectsDateBeforeTreasuryHistory() throws Exception {
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "description": "Hotel",
                                  "transactionDate": "2000-03-10",
                                  "purchaseAmount": 12.34
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("validation failed"))
                .andExpect(jsonPath("$.details[0]").value("transactionDate: must be on or after 2001-03-31"));
    }

    @Test
    void getPurchaseReturnsConvertedTransaction() throws Exception {
        UUID purchaseId = UUID.randomUUID();
        purchaseTransactionService.convertedResponse = new ConvertedPurchaseTransactionResponse(
                purchaseId,
                "Taxi",
                LocalDate.of(2026, 2, 10),
                new BigDecimal("18.25"),
                "Canada-Dollar",
                LocalDate.of(2026, 1, 31),
                new BigDecimal("1.4234"),
                new BigDecimal("25.97")
        );

        mockMvc.perform(get("/api/purchases/{purchaseId}", purchaseId)
                        .queryParam("countryCurrency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCurrency").value("Canada-Dollar"))
                .andExpect(jsonPath("$.convertedAmount").value(25.97));
    }

    @Test
    void getPurchaseReturnsUnprocessableEntityWhenConversionIsUnavailable() throws Exception {
        purchaseTransactionService.conversionException = new CurrencyConversionNotAvailableException(
                "Canada-Dollar",
                LocalDate.of(2026, 2, 10)
        );

        mockMvc.perform(get("/api/purchases/{purchaseId}", UUID.randomUUID())
                        .queryParam("countryCurrency", "Canada-Dollar"))
                .andExpect(status().isUnprocessableEntity());
    }

    private static final class StubPurchaseTransactionService extends PurchaseTransactionService {
        private PurchaseTransactionResponse createResponse;
        private ConvertedPurchaseTransactionResponse convertedResponse;
        private RuntimeException conversionException;

        private StubPurchaseTransactionService() {
            super(null, null);
        }

        @Override
        public PurchaseTransactionResponse createPurchase(CreatePurchaseTransactionRequest request) {
            if (request.transactionDate().isBefore(CreatePurchaseTransactionRequest.MIN_SUPPORTED_TREASURY_DATE)) {
                throw new IllegalArgumentException("must be on or after 2001-03-31");
            }
            return createResponse;
        }

        @Override
        public ConvertedPurchaseTransactionResponse getConvertedPurchase(UUID purchaseId, String countryCurrency) {
            if (conversionException != null) {
                throw conversionException;
            }
            return convertedResponse;
        }
    }
}
