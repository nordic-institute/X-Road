@SecurityServer
Feature: Security Server API

  Scenario: Security server get certs fails
    When Security server auth certs for "123" is requested
    Then Response is of status code 403
