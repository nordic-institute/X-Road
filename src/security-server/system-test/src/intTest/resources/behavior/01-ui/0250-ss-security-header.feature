@SecurityServer
Feature: 0250 - SS: Security headers

  Scenario: Verify that content security headers are set correctly
    When SecurityServer login page is open
    Then the response should have a valid CSP nonce in the header and tags
