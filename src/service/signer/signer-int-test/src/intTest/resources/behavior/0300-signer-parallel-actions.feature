Feature: 0300 - Signer: Parallel scenarios
  Uses SoftHSM to emulate hardware token.

  Background:
    Given tokens are listed
    And HSM is operational

  Scenario: Data sign is properly handled in parallel execution
    When digest can be signed in using key "SignKey from CA" from token "xrd-softhsm-0". Called 50 times with 25 threads in parallel.

  Scenario: Data sign is properly handled in parallel execution (post restart)
    When signer service is restarted
    And tokens are listed
    And digest can be signed in using key "SignKey from CA" from token "xrd-softhsm-0". Called 25 times with 5 threads in parallel.
