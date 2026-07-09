@Dataspaces
Feature: 0300 - Data spaces baseline

  Scenario: Asset is created on ss0
    Given Asset is created on "ss0"
    And Policy definition allowing only "ss1" is created on "ss0"
    And Contract definition is created on "ss0"

  Scenario: Consumer retrieves data through data space
    Then Catalog can be retrieved on "ss1" from "ss0"
    And Contract negotiation is initiated on "ss1" with provider "ss0"
    And Contract negotiation state is "FINALIZED" on "ss1"
    And Transfer process is started on "ss1" with provider "ss0"
    And Transfer process is in state "STARTED" on "ss1"
    Then Asset access response is retrieved on "ss1"

  # X-Road asset-access acquisition is exposed only over gRPC (AssetAccessGrpcService); there is no
  # management REST endpoint for it, so this scenario cannot be driven via the management API.
  #  Scenario: Consumer acquires asset access via control plane API
  #    Then Asset access is acquired via control plane API on "ss1" from "ss0" for asset "DEV:COM:1234:TestService:getRandom"
