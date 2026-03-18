package com.wex.payments.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@ScenarioScope
class BddScenarioContext {

    private final Map<String, String> purchasePayload = new LinkedHashMap<>();
    private ResultActions resultActions;
    private JsonNode responseBody;
    private UUID latestPurchaseId;

    Map<String, String> getPurchasePayload() {
        return purchasePayload;
    }

    void clearPurchasePayload() {
        purchasePayload.clear();
    }

    ResultActions getResultActions() {
        return resultActions;
    }

    void setResultActions(ResultActions resultActions) {
        this.resultActions = resultActions;
    }

    JsonNode getResponseBody() {
        return responseBody;
    }

    void setResponseBody(JsonNode responseBody) {
        this.responseBody = responseBody;
    }

    UUID getLatestPurchaseId() {
        return latestPurchaseId;
    }

    void setLatestPurchaseId(UUID latestPurchaseId) {
        this.latestPurchaseId = latestPurchaseId;
    }
}
