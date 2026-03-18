Feature: Retrieve a stored purchase transaction in a specified country's currency
  In order to view stored purchases in another supported currency
  As an API consumer
  I want to retrieve a stored purchase with the exchange rate active for the purchase date

  Background:
    Given a stored purchase transaction exists with:
      | id              | 9fd2d930-aebc-45b7-b0be-53b33e6e5330 |
      | description     | Hotel                                |
      | transactionDate | 2026-02-10                           |
      | purchaseAmount  | 100.00                               |

  Scenario: Retrieve a stored purchase converted with the latest rate on or before the purchase date
    Given Treasury exchange rates include:
      | countryCurrency | exchangeRateDate | exchangeRate |
      | Canada-Dollar   | 2026-01-31       | 1.4234       |
    When the client retrieves purchase "9fd2d930-aebc-45b7-b0be-53b33e6e5330" in "Canada-Dollar"
    Then the response status should be 200
    And the response should contain:
      | id                        | 9fd2d930-aebc-45b7-b0be-53b33e6e5330 |
      | description               | Hotel                                |
      | transactionDate           | 2026-02-10                           |
      | originalPurchaseAmountUsd | 100.00                               |
      | exchangeRateDate          | 2026-01-31                           |
      | exchangeRate              | 1.4234                               |
      | convertedAmount           | 142.34                               |

  Scenario: Use the nearest earlier exchange rate within the last six months when there is no exact date match
    Given Treasury exchange rates include:
      | countryCurrency | exchangeRateDate | exchangeRate |
      | Canada-Dollar   | 2025-12-31       | 1.4000       |
      | Canada-Dollar   | 2026-01-31       | 1.4234       |
    When the client retrieves purchase "9fd2d930-aebc-45b7-b0be-53b33e6e5330" in "Canada-Dollar"
    Then the response status should be 200
    And the selected exchange rate date should be "2026-01-31"
    And the converted amount should be "142.34"

  Scenario: Round the converted amount to two decimal places
    Given Treasury exchange rates include:
      | countryCurrency | exchangeRateDate | exchangeRate |
      | Canada-Dollar   | 2026-01-31       | 1.33335      |
    When the client retrieves purchase "9fd2d930-aebc-45b7-b0be-53b33e6e5330" in "Canada-Dollar"
    Then the response status should be 200
    And the converted amount should be "133.34"

  Scenario: Return an error when no exchange rate is available on or before the purchase date within six months
    Given Treasury exchange rates do not contain an entry for "Canada-Dollar" on or before "2026-02-10" within the last 6 months
    When the client retrieves purchase "9fd2d930-aebc-45b7-b0be-53b33e6e5330" in "Canada-Dollar"
    Then the response status should be 422
    And the error message should be "purchase dated 2026-02-10 cannot be converted to 'Canada-Dollar' because no exchange rate exists on or before that date within the last 6 months"

  Scenario: Return an error when the purchase identifier does not exist
    Given Treasury exchange rates include:
      | countryCurrency | exchangeRateDate | exchangeRate |
      | Canada-Dollar   | 2026-01-31       | 1.4234       |
    When the client retrieves purchase "de305d54-75b4-431b-adb2-eb6b9e546014" in "Canada-Dollar"
    Then the response status should be 404
    And the error message should be "purchase with id 'de305d54-75b4-431b-adb2-eb6b9e546014' was not found"
