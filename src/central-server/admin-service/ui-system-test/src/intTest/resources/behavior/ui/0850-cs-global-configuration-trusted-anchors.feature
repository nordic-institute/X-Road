@CentralServer
Feature: 0850 - CS: Global configuration: Trusted Anchors

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Global configuration tab is selected
    And Trusted Anchors sub-tab is selected

  Scenario: User can upload trusted anchor
    Given trusted anchors list not contains instance CS2-E2E
    When user clicks Upload button
    And user uploads trusted anchor from file trusted-anchor-CS2-E2E.xml
    And confirmation is asked and user confirm anchor upload
    Then trusted anchor is successfully uploaded
    And trusted anchor CS2-E2E with created 2023-02-15 11:26:34 is displayed in list

  Scenario: User can download trusted anchor
    Given trusted anchor CS2-E2E with created 2023-02-15 11:26:34 is displayed in list
    When user clicks trusted anchor CS2-E2E Download button
    Then trusted anchor is successfully downloaded

  Scenario: User can delete trusted anchor
    Given trusted anchor CS2-E2E with created 2023-02-15 11:26:34 is displayed in list
    When user clicks trusted anchor CS2-E2E Delete button
    And confirmation is asked and user confirm anchor delete
    Then trusted anchor is successfully deleted
    And trusted anchors list not contains instance CS2-E2E
