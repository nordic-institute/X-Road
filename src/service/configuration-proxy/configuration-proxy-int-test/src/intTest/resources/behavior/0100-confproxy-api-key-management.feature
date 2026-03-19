Feature: 0100 - Configuration Proxy: API key management via CLI

  Scenario: Generating new API keys
    Given there are no API keys
    When new API key is generated via CLI
    Then the generated key output contains API key value, ID and roles
    And API key list contains key with ID 1
    And the key list output contains ID and roles but not API key value
    When new API key is generated via CLI
    Then API key list contains key with ID 1
    And API key list contains key with ID 2

  Scenario: Revoking API keys
    When API key with ID 1 is revoked via CLI
    Then API key list does not contain key with ID 1
    And API key list contains key with ID 2

  Scenario: List configured proxy instances via REST API
    Given new API key is generated via CLI
    When proxy instances are listed via REST using last generated API key
    Then the instances response is successful
