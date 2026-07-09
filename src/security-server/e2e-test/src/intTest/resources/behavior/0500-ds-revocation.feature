# Revoking a member credential is permanent for the lifetime of the shared test stack and
# affects every later data-space negotiation by that member. This feature therefore runs last
# (highest number), after all scenarios that depend on ss1's credentials.
@Dataspaces
Feature: 0500 - Data spaces credential revocation

  Scenario: Consumer with a revoked credential cannot negotiate a contract
    Given Asset is created on "ss0"
    And Policy definition allowing only "ss1" is created on "ss0"
    And Contract definition is created on "ss0"
    And Catalog can be retrieved on "ss1" from "ss0"
    When Credential for X-Road member "DEV:COM:4321" is revoked at the issuer on "aux"
    And Contract negotiation is initiated on "ss1" with provider "ss0"
    Then Contract negotiation reaches terminal state "TERMINATED" on "ss1"
