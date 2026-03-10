@SecurityServer
@DataSpaces
Feature: 5000 - DS control plane tests

  Scenario: Participant context is created
    Given Participant context "test-part-ctx" is created
    And Participant context "test-part-ctx" config is created
    And Asset is created in participant context "test-part-ctx"
    And Policy definition is created in participant context "test-part-ctx"
    And Contract definition is created in participant context "test-part-ctx"
    Then Catalog can be retrieved from participant context "test-part-ctx"
    And Participant context "test-part-ctx" can be retrieved
