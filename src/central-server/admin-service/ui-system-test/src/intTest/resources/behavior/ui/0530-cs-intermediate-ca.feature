@CentralServer
@CertificationService
@IntermediateCA
Feature: 0530 - CS: Trust Services -> CA Details -> Intermediate CAs

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

    When TrustServices tab is selected
    And new certification service is added
    And user opens certification service details
    And Intermediate CAs tab is selected

  Scenario: Intermediate CA can be added
    When Intermediate CA with name E2e-test CA is added
    Then Intermediate CA with name E2e-test CA is visible in the Intermediate CA list

  Scenario: Intermediate CA list is correctly shown
    When Intermediate CAs table is visible
    And Intermediate CA with name E2e-test CA is added
    Then User is able to sort Intermediate CAs by header column 1
    And User is able to sort Intermediate CAs by header column 2
    And User is able to sort Intermediate CAs by header column 3
    And User is able to view the certificate of Intermediate CA with name E2e-test CA

  Scenario: Intermediate CA can be delete in list
    When Intermediate CA with name E2e-test CA is added
    Then User is able to click delete button in Intermediate CA with name E2e-test CA
    And Intermediate CA with name E2e-test CA should be removed in list
