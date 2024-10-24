@HardwareToken
Feature: 0200 - Signer: HardwareToken
  Uses SoftHSM to emulate hardware token.

  Background:
    Given tokens are listed

  Scenario: HSM is operational
    * HSM is operational

  Scenario: Token has its friendly name updated
    When friendly name "xrd-softhsm-0" is set for token with label "x-road-softhsm2"
    Then token with label "x-road-softhsm2" name is "xrd-softhsm-0"

  Scenario: Token is in initialized
    Given tokens list contains token "xrd-softhsm-0"
    And token "xrd-softhsm-0" status is "OK"

  Scenario: Token is activated
    Given token "xrd-softhsm-0" is not active
    When token "xrd-softhsm-0" is logged in with pin "1234"
    Then token "xrd-softhsm-0" is active

  Scenario: Token is deactivated
    When token "xrd-softhsm-0" is logged out
    Then token "xrd-softhsm-0" is not active

  Scenario: Token pin update is not supported for hardware token
    Given token "xrd-softhsm-0" is not active
    And token "xrd-softhsm-0" is logged in with pin "1234"
    When token "xrd-softhsm-0" pin is update from "1234" to "4321" fails with an error
    Then token "xrd-softhsm-0" is active
