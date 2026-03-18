package com.wex.payments.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wex.payments.repository.PurchaseTransactionRepository;
import com.wex.payments.service.ExchangeRateQuote;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PurchaseTransactionBddSteps {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PurchaseTransactionRepository repository;
    private final StubExchangeRateClient stubExchangeRateClient;
    private final BddScenarioContext context;

    @Autowired
    public PurchaseTransactionBddSteps(MockMvc mockMvc,
                                       ObjectMapper objectMapper,
                                       JdbcTemplate jdbcTemplate,
                                       PurchaseTransactionRepository repository,
                                       StubExchangeRateClient stubExchangeRateClient,
                                       BddScenarioContext context) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
        this.stubExchangeRateClient = stubExchangeRateClient;
        this.context = context;
    }

    @Before
    public void resetState() {
        repository.deleteAll();
        stubExchangeRateClient.reset();
        context.clearPurchasePayload();
        context.setResultActions(null);
        context.setResponseBody(null);
        context.setLatestPurchaseId(null);
    }

    @Given("the purchase payload contains:")
    public void thePurchasePayloadContains(DataTable dataTable) {
        context.clearPurchasePayload();
        context.getPurchasePayload().putAll(dataTable.asMap(String.class, String.class));
    }

    @When("the client submits the purchase transaction")
    public void theClientSubmitsThePurchaseTransaction() throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        Map<String, String> payload = context.getPurchasePayload();
        requestBody.put("description", payload.get("description"));
        requestBody.put("transactionDate", payload.get("transactionDate"));
        requestBody.put("purchaseAmount", new BigDecimal(payload.get("purchaseAmount")));

        context.setResultActions(mockMvc.perform(post("/api/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(requestBody))));
        captureResponse();
    }

    @Then("the purchase transaction should be stored")
    public void thePurchaseTransactionShouldBeStored() {
        assertThat(repository.count()).isEqualTo(1);
        assertThat(context.getLatestPurchaseId()).isNotNull();
        assertThat(repository.existsById(context.getLatestPurchaseId())).isTrue();
    }

    @And("the response status should be {int}")
    public void theResponseStatusShouldBe(int statusCode) throws Exception {
        context.getResultActions().andExpect(status().is(statusCode));
    }

    @And("the response should contain a generated unique identifier")
    public void theResponseShouldContainAGeneratedUniqueIdentifier() {
        JsonNode responseBody = context.getResponseBody();
        assertThat(responseBody.hasNonNull("id")).isTrue();

        UUID parsedId = UUID.fromString(responseBody.get("id").asText());
        context.setLatestPurchaseId(parsedId);

        assertThat(repository.existsById(parsedId)).isTrue();
    }

    @And("the response should contain:")
    public void theResponseShouldContain(DataTable dataTable) {
        JsonNode responseBody = context.getResponseBody();

        for (Map.Entry<String, String> expectedField : dataTable.asMap(String.class, String.class).entrySet()) {
            JsonNode actualNode = responseBody.get(expectedField.getKey());
            assertThat(actualNode)
                    .as("response field %s", expectedField.getKey())
                    .isNotNull();
            assertFieldValue(actualNode, expectedField.getValue());
        }
    }

    @And("the validation error should be {string}")
    public void theValidationErrorShouldBe(String expectedError) {
        List<String> details = objectMapper.convertValue(context.getResponseBody().get("details"), objectMapper.getTypeFactory()
                .constructCollectionType(List.class, String.class));
        assertThat(details).contains(expectedError);
    }

    @Given("a stored purchase transaction exists with:")
    public void aStoredPurchaseTransactionExistsWith(DataTable dataTable) {
        Map<String, String> values = dataTable.asMap(String.class, String.class);
        jdbcTemplate.update(
                "insert into purchase_transactions (id, description, transaction_date, purchase_amount_usd) values (?, ?, ?, ?)",
                UUID.fromString(values.get("id")),
                values.get("description"),
                LocalDate.parse(values.get("transactionDate")),
                new BigDecimal(values.get("purchaseAmount"))
        );
    }

    @Given("Treasury exchange rates include:")
    public void treasuryExchangeRatesInclude(DataTable dataTable) {
        List<ExchangeRateQuote> quotes = dataTable.asMaps(String.class, String.class).stream()
                .map(row -> new ExchangeRateQuote(
                        row.get("countryCurrency"),
                        LocalDate.parse(row.get("exchangeRateDate")),
                        new BigDecimal(row.get("exchangeRate"))))
                .toList();

        stubExchangeRateClient.setQuotes(quotes);
    }

    @Given("Treasury exchange rates do not contain an entry for {string} on or before {string} within the last 6 months")
    public void treasuryExchangeRatesDoNotContainAnEntryForOnOrBeforeWithinTheLastMonths(String countryCurrency, String purchaseDate) {
        stubExchangeRateClient.setQuotes(List.of(
                new ExchangeRateQuote(countryCurrency, LocalDate.parse(purchaseDate).minusMonths(7), new BigDecimal("1.1111"))
        ));
    }

    @When("the client retrieves purchase {string} in {string}")
    public void theClientRetrievesPurchaseIn(String purchaseId, String countryCurrency) throws Exception {
        context.setResultActions(mockMvc.perform(get("/api/purchases/{purchaseId}", purchaseId)
                .queryParam("countryCurrency", countryCurrency)));
        captureResponse();
    }

    @And("the selected exchange rate date should be {string}")
    public void theSelectedExchangeRateDateShouldBe(String expectedDate) {
        assertThat(context.getResponseBody().path("exchangeRateDate").asText()).isEqualTo(expectedDate);
    }

    @And("the converted amount should be {string}")
    public void theConvertedAmountShouldBe(String expectedAmount) {
        assertFieldValue(context.getResponseBody().path("convertedAmount"), expectedAmount);
    }

    @And("the error message should be {string}")
    public void theErrorMessageShouldBe(String expectedMessage) {
        assertThat(context.getResponseBody().path("message").asText()).isEqualTo(expectedMessage);
    }

    private void captureResponse() throws Exception {
        MvcResult mvcResult = context.getResultActions().andReturn();
        String content = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (content == null || content.isBlank()) {
            context.setResponseBody(objectMapper.createObjectNode());
            return;
        }

        JsonNode responseBody = objectMapper.readTree(content);
        context.setResponseBody(responseBody);
        if (responseBody.hasNonNull("id")) {
            context.setLatestPurchaseId(UUID.fromString(responseBody.get("id").asText()));
        }
    }

    private void assertFieldValue(JsonNode actualNode, String expectedValue) {
        if (actualNode.isNumber()) {
            assertThat(actualNode.decimalValue()).isEqualByComparingTo(new BigDecimal(expectedValue));
            return;
        }

        assertThat(actualNode.asText()).isEqualTo(expectedValue);
    }
}
