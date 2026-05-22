@Dataspaces
Feature: 0300 - Data spaces baseline

  Scenario: Issuer service is provisioned
    Given Issuer Service participant context "issuer" with DID "did:web:ds-issuer-service%3A6183:issuer" and issuer service endpoint "http://ds-issuer-service:6185/api/issuance/v1alpha/participants/aXNzdWVy" is created on "aux"
    And Holder for DID "did:web:ss0-ds-identity-hub%3A7183" is created for "issuer" on "aux"
    And Holder for DID "did:web:ss1-ds-identity-hub%3A7183" is created for "issuer" on "aux"
    And "xroad-membership" attestation definition is created for "issuer" on "aux"
    And "xroad-membership" credential definition is created for "issuer" on "aux"

  Scenario: SS0 Identity Hub is provisioned
    Given Identity Hub participant context "test-part-ctx" with DID "did:web:ss0-ds-identity-hub%3A7183" and credential service endpoint "http://ss0-ds-identity-hub:7185/api/credentials/v1/participants/dGVzdC1wYXJ0LWN0eA==" for X-Road member "DEV/COM/SS0" is created on "ss0"
    And "xroad-membership" credential request from issuer "did:web:ds-issuer-service%3A6183:issuer" is submitted for "test-part-ctx" on "ss0"
    And "xroad-membership" credential request for participant "test-part-ctx" reaches status "ISSUED" on "ss0"

  Scenario: SS1 Identity Hub is provisioned
    Given Identity Hub participant context "test-part-ctx" with DID "did:web:ss1-ds-identity-hub%3A7183" and credential service endpoint "http://ss1-ds-identity-hub:7185/api/credentials/v1/participants/dGVzdC1wYXJ0LWN0eA==" for X-Road member "DEV/COM/SS1" is created on "ss1"
    And "xroad-membership" credential request from issuer "did:web:ds-issuer-service%3A6183:issuer" is submitted for "test-part-ctx" on "ss1"
    And "xroad-membership" credential request for participant "test-part-ctx" reaches status "ISSUED" on "ss1"

  Scenario: Asset is created on ss1
    Given Participant context "test-part-ctx" with DID "did:web:ss1-ds-identity-hub%3A7183" is created on "ss1"
    And Participant context "test-part-ctx" config with DID "did:web:ss1-ds-identity-hub%3A7183" is created on "ss1"
    And Asset is created in participant context "test-part-ctx" on "ss1"
    And Policy definition allowing only X-Road member "DEV/COM/SS0" is created in participant context "test-part-ctx" on "ss1"
    And Contract definition is created in participant context "test-part-ctx" on "ss1"

  Scenario: Consumer retrieves data through data space
    Given Participant context "test-part-ctx" with DID "did:web:ss0-ds-identity-hub%3A7183" is created on "ss0"
    And Participant context "test-part-ctx" config with DID "did:web:ss0-ds-identity-hub%3A7183" is created on "ss0"
    Then Catalog can be retrieved using participant context "test-part-ctx" on "ss0" from "did:web:ss1-ds-identity-hub%3A7183" on "ss1"
    And Contract negotiation is initiated using participant context "test-part-ctx" on "ss0" with provider "did:web:ss1-ds-identity-hub%3A7183" on "ss1"
    And Contract negotiation state is "FINALIZED" using participant context "test-part-ctx" on "ss0"
    And Transfer process is started using participant context "test-part-ctx" on "ss0" with provider "did:web:ss1-ds-identity-hub%3A7183" on "ss1"
    And Transfer process is in state "STARTED" using participant context "test-part-ctx" on "ss0"
    Then EDR is retrieved on "ss0"

  Scenario: Consumer acquires EDR via xroad-edr-api endpoint
    Then EDR is acquired via xroad-edr-api for context "test-part-ctx" on "ss0" from "did:web:ss1-ds-identity-hub%3A7183" on "ss1" for asset "asset-1"
