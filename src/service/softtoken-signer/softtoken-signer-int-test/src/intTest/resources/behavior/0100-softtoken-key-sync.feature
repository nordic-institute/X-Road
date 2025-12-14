@SoftToken
@KeySync
Feature: 0100 - Softtoken-Signer: Key Synchronization

  Background:
    Given signer is initialized with PIN "1234"
    And token "soft-token-000" is logged in with PIN "1234"

  Scenario: Keys are synchronized on startup
    Given signer has RSA key "rsa-key" on token "soft-token-000"
    And signer has EC key "ec-key" on token "soft-token-000"
    When key synchronization completes
    Then softtoken-signer can sign with key "rsa-key"
    And softtoken-signer can sign with key "ec-key"

  Scenario: New key is synchronized after creation
    Given signer has RSA key "existing-key" on token "soft-token-000"
    And key synchronization completes
    When new RSA key "new-sync-key" generated for token "soft-token-000" in signer
    And key synchronization completes
    Then softtoken-signer can sign with key "new-sync-key"

  Scenario: Key deletion is synchronized
    Given signer has RSA key "key-to-delete" on token "soft-token-000"
    And key synchronization completes
    When key "key-to-delete" is deleted from signer
    And key synchronization completes
    Then softtoken-signer cannot sign with key "key-to-delete"

  Scenario: Signature created by softtoken-signer is valid
    Given signer has EC key "sign-test-key" on token "soft-token-000"
    And key synchronization completes
    When signature is created with softtoken-signer using key "sign-test-key"
    Then signature can be verified with key "sign-test-key" public key
