@CentralServer
@ConfigurationAnchor
@LoadingTesting
Feature: 0710 - CS: Global configuration: External configuration: Anchor

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Global configuration tab is selected
    And External configuration sub-tab is selected

  Scenario: User can recreate anchor
    Given Current configuration anchor information is marked
    When Configuration anchor is recreated
    Then Updated anchor information is displayed

  Scenario: User can download anchor
    When User clicks configuration anchor download button
    Then Configuration anchor is successfully downloaded
    And Downloaded anchor has 1 certificates for http source
    And Downloaded anchor has 1 certificates for https source

  Scenario: Anchor is updated if new signing key is added
    Given Details for Token: softToken-0 is expanded
    And Current configuration anchor information is marked
    When User adds signing key for token: softToken-0 with name: anchor-test-1
    Then Updated anchor information is displayed
    When User clicks configuration anchor download button
    Then Configuration anchor is successfully downloaded
    And Downloaded anchor has 2 certificates for http source
    And Downloaded anchor has 2 certificates for https source

  Scenario: Anchor is updated if signing key is deleted
    Given Details for Token: softToken-0 is expanded
    And Current configuration anchor information is marked
    When User deletes signing key: anchor-test-1 for token: softToken-0
    Then Updated anchor information is displayed
    When User clicks configuration anchor download button
    Then Configuration anchor is successfully downloaded
    And Downloaded anchor has 1 certificates for http source
    And Downloaded anchor has 1 certificates for https source
