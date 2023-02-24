@SecurityServer
Feature: System Status API

  Background:
    Given Authentication header is set to SYSTEM_ADMINISTRATOR

  Scenario: Verifying that system status is OK
    When System status is requested
    Then System status is validated

  Scenario: Verify system version endpoint works
    * System version endpoint returns version
