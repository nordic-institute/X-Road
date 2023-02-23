@SecurityServer
Feature: Security Server API

  Background:
    Given Authentication header is set to SYSTEM_ADMINISTRATOR

  Scenario: Security server get certs fails
    When Security server auth certs for "123" is requested
    Then Response is of status code 403
