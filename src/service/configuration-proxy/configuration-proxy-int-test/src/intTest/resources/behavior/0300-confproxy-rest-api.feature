Feature: 0300 - Configuration Proxy: Instance management via REST API

  Background:
    Given new API key is generated via CLI

  Scenario: List configured instances via REST API
    When configured instances are listed via REST API
    Then the REST response contains instance "TEST"

  Scenario: Get instance details via REST API
    When instance "TEST" details are retrieved via REST API
    Then the REST response instance name is "TEST"
    And the REST response instance has 1 signing key
    And the REST response instance is configured

  Scenario: Add signing key via REST API
    When a signing key is added to instance "TEST" from token "0" via REST API
    Then the REST response instance has 2 signing keys

  Scenario: Activate signing key via REST API
    When the second signing key of instance "TEST" is activated via REST API
    Then the REST response instance has 2 signing keys
    And the second signing key of the REST response is active

  Scenario: Delete non-active signing key via REST API
    When the non-active signing key of instance "TEST" is deleted via REST API
    Then the REST response instance has 1 signing key

  Scenario: Generate anchor via REST API
    When anchor is generated for instance "TEST" via REST API
    Then the REST response contains a valid anchor XML
