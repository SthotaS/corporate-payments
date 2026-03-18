Feature: Store a purchase transaction
  In order to track purchase activity
  As an API consumer
  I want to submit a purchase transaction and receive a unique identifier

  Scenario: Store a valid purchase transaction
    Given the purchase payload contains:
      | description     | Office supplies |
      | transactionDate | 2026-03-15      |
      | purchaseAmount  | 123.45          |
    When the client submits the purchase transaction
    Then the purchase transaction should be stored
    And the response status should be 201
    And the response should contain a generated unique identifier
    And the response should contain:
      | description       | Office supplies |
      | transactionDate   | 2026-03-15      |
      | purchaseAmountUsd | 123.45          |

  Scenario: Round a purchase amount to the nearest cent before storing
    Given the purchase payload contains:
      | description     | Team lunch |
      | transactionDate | 2026-03-15 |
      | purchaseAmount  | 123.456    |
    When the client submits the purchase transaction
    Then the purchase transaction should be stored
    And the response status should be 201
    And the response should contain:
      | description       | Team lunch |
      | transactionDate   | 2026-03-15 |
      | purchaseAmountUsd | 123.46     |

  Scenario Outline: Reject a purchase transaction with invalid required fields
    Given the purchase payload contains:
      | description     | <description>     |
      | transactionDate | <transactionDate> |
      | purchaseAmount  | <purchaseAmount>  |
    When the client submits the purchase transaction
    Then the response status should be 400
    And the validation error should be "<validationError>"

    Examples:
      | description                                            | transactionDate | purchaseAmount | validationError                            |
      | This description is intentionally longer than fifty ch | 2026-03-15      | 10.00          | description: must not exceed 50 characters |
      | Hotel                                                  | 03/15/2026      | 10.00          | transactionDate: must be a valid date format |
      | Hotel                                                  | 2026-03-15      | -1.00          | purchaseAmount: must be positive           |

  Scenario: Reject a purchase transaction dated in the future
    Given the purchase payload contains:
      | description     | Conference hotel |
      | transactionDate | 2099-03-10       |
      | purchaseAmount  | 200.00           |
    When the client submits the purchase transaction
    Then the response status should be 400
    And the validation error should be "transactionDate: transactionDate must not be in the future"
