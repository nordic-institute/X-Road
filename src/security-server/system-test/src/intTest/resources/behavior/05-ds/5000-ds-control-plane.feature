@SecurityServer
@DataSpaces
@Skip
Feature: 5000 - DS control plane tests

  Scenario: Catalog can be retrieved over DSP protocol
    Then Catalog can be retrieved using participant context "ss0" with DID "did:web:ds-identity-hub%3A7183"
    And Participant context "ss0" can be retrieved
