# Revoking a member credential is permanent for the lifetime of the shared test stack and
# affects every later data-space negotiation by that member. This feature therefore runs last
# (highest number), after all scenarios that depend on ss1's credentials.
@Dataspaces @Skip
Feature: 0500 - Data spaces credential revocation

  Scenario: Consumer with a revoked credential cannot negotiate a contract
    Given Asset is created in participant context "xrd-ss0" on "ss0"
    And Policy definition allowing only "did:web:ss1-ds-identity-hub%3A7183" is created in participant context "xrd-ss0" on "ss0"
    And Contract definition is created in participant context "xrd-ss0" on "ss0"
    And Catalog can be retrieved using participant context "xrd-ss1" on "ss1" from "did:web:ss0-ds-identity-hub%3A7183" on "ss0"
    When Credential for X-Road member "DEV:COM:4321" is revoked at the issuer on "aux"
    And Contract negotiation is initiated using participant context "xrd-ss1" on "ss1" with provider "did:web:ss0-ds-identity-hub%3A7183" on "ss0"
    Then Contract negotiation reaches terminal state "TERMINATED" using participant context "xrd-ss1" on "ss1"
