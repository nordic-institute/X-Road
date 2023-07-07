@CentralServer
@ConfigurationAnchor
@LoadingTesting
Feature: 0700 - CS: Global configuration: Internal configuration: Anchor

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Global configuration tab is selected
    And Internal configuration sub-tab is selected

  Scenario: User can recreate anchor
    When Configuration anchor is recreated
    Then Updated anchor information is displayed

  Scenario: User can download anchor
    When User clicks configuration anchor download button
    Then Configuration anchor is successfully downloaded
