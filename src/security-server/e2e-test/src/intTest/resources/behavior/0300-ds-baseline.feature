@Dataspaces
@Skip
Feature: 0300 - Data spaces baseline

  Scenario: Asset is created on ss1
    Given Asset is created in participant context "test-part-ctx" on "ss1"
    And Policy definition allowing only "did:web:ss0-ds-identity-hub%3A7183" is created in participant context "test-part-ctx" on "ss1"
    And Contract definition is created in participant context "test-part-ctx" on "ss1"

  Scenario: Consumer retrieves data through data space
    Then Catalog can be retrieved using participant context "test-part-ctx" on "ss0" from "did:web:ss1-ds-identity-hub%3A7183" on "ss1"
    And Contract negotiation is initiated using participant context "test-part-ctx" on "ss0" with provider "did:web:ss1-ds-identity-hub%3A7183" on "ss1"
    And Contract negotiation state is "FINALIZED" using participant context "test-part-ctx" on "ss0"
    And Transfer process is started using participant context "test-part-ctx" on "ss0" with provider "did:web:ss1-ds-identity-hub%3A7183" on "ss1"
    And Transfer process is in state "STARTED" using participant context "test-part-ctx" on "ss0"
    Then Asset access response is retrieved on "ss0"

  Scenario: Consumer acquires asset access via control plane API
    Then Asset access is acquired via control plane API for context "test-part-ctx" on "ss0" from "did:web:ss1-ds-identity-hub%3A7183" on "ss1" for asset "asset-1"
