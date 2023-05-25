@CentralServer
@CertificationService
Feature: 0500 - CS: Trust Services

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret
    And TrustServices tab is selected

  Scenario: Add certification service
    When new certification service is added
    Then new certification service is visible in the Certification Services list
    And user is able to sort by column 1
    And user is able to sort by column 2
    And user is able to sort by column 3

  Scenario: Delete certification service
    Given new certification service is added
    And new certification service is visible in the Certification Services list
    And user opens certification service details
    When user clicks on delete trust service
    Then certification service is not visible in the Certification Services list

  Scenario: View certification service details
    Given new certification service is added
    When user opens certification service details
    Then certification service details are displayed
    And user is able to view the certificate

  Scenario: View and change certification service settings
    Given new certification service is added
    And user opens certification service details
    When user navigates to CA settings
    Then CA settings are shown
    And user can change the TLS Auth setting
    And user can change the certificate profile
