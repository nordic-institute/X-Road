@Dataspaces @Skip
Feature: 0300 - Data spaces baseline

  Scenario: Asset is created on ss0
    Given Asset is created in participant context "xrd-ss0" on "ss0"
    And Policy definition allowing only "did:web:ss1-ds-identity-hub%3A7183" is created in participant context "xrd-ss0" on "ss0"
    And Contract definition is created in participant context "xrd-ss0" on "ss0"

  Scenario: Consumer retrieves data through data space
    Then Catalog can be retrieved using participant context "xrd-ss1" on "ss1" from "did:web:ss0-ds-identity-hub%3A7183" on "ss0"
    And Contract negotiation is initiated using participant context "xrd-ss1" on "ss1" with provider "did:web:ss0-ds-identity-hub%3A7183" on "ss0"
    And Contract negotiation state is "FINALIZED" using participant context "xrd-ss1" on "ss1"
    And Transfer process is started using participant context "xrd-ss1" on "ss1" with provider "did:web:ss0-ds-identity-hub%3A7183" on "ss0"
    And Transfer process is in state "STARTED" using participant context "xrd-ss1" on "ss1"
    Then Asset access response is retrieved on "ss1"

  # X-Road asset-access acquisition is exposed only over gRPC (AssetAccessGrpcService); there is no
  # management REST endpoint for it, so this scenario cannot be driven via the management API.
  #  Scenario: Consumer acquires asset access via control plane API
  #    Then Asset access is acquired via control plane API for context "xrd-ss1" on "ss1" from "did:web:ss0-ds-identity-hub%3A7183" on "ss0" for asset "DEV:COM:1234:TestService:getRandom"
