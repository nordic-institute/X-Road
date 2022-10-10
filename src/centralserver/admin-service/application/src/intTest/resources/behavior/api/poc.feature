@SecurityServer
Feature: POC

  Scenario: Making sure feign client is working
    When System status is requested
    Then System status is validated
