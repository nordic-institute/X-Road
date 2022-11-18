@SecurityServer
Feature: System Status API

  Scenario: Verifying that system status is OK
    When System status is requested
    Then System status is validated
