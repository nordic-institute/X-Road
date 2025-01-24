@SoftToken
Feature: 0100 - Proxy: Batch signer

  Background:
    Given tokens are listed

  Scenario: Token and key is initialized
    When token is initialized with pin "123456"
    And token with id "0" is logged in with pin "123456"
    Then tokens are listed

  Scenario: Batch signer can sign multiple messages
    Given new key "key-1" generated for token with id "0"
    And the SIGNING cert request is generated with created key for client "DEV:COM:1234:MANAGEMENT"
    And SIGN CSR is processed by test CA
    And Generated certificate with initial status "registered" is imported for client "DEV:COM:1234:MANAGEMENT"
    And token info can be retrieved by key id
    And tokens are listed
    When client "DEV:COM:1234:MANAGEMENT" signs the messages 500 random messages using 50 threads
