@Dataspaces
Feature: 0300 - Data spaces baseline

  Scenario: Asset is created on ss2
    Given Participant context "test-part-ctx" is created on "ss1"
    And Participant context "test-part-ctx" config is created on "ss1"
    And Asset is created in participant context "test-part-ctx" on "ss1"
    And Policy definition is created in participant context "test-part-ctx" on "ss1"
    And Contract definition is created in participant context "test-part-ctx" on "ss1"

  Scenario: Consumer retrieves data through data space
    Given Participant context "test-part-ctx" is created on "ss0"
    And Participant context "test-part-ctx" config is created on "ss0"
    Then Catalog can be retrieved using participant context "test-part-ctx" on "ss0" from "ss1"
    And Contract negotiation is initiated using participant context "test-part-ctx" on "ss0" with provider "ss1"
    And Contract negotiation state is "FINALIZED" using participant context "test-part-ctx" on "ss0"
    And Transfer process is started using participant context "test-part-ctx" on "ss0" with provider "ss1"
    And Transfer process is in state "STARTED" using participant context "test-part-ctx" on "ss0"
    Then EDR is retrieved on "ss0"
