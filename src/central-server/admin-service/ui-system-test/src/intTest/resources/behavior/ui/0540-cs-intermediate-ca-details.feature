@CentralServer
@CertificationService
@IntermediateCA
Feature: 0540 - CS: Trust Services -> CA Details -> Intermediate CAs -> Intermediate CA Details

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

    When TrustServices tab is selected
    And new certification service is added
    And user opens certification service details
    And Intermediate CAs tab is selected
    And Intermediate CA with name E2e-test CA is added

  Scenario: Intermediate CA details can be viewed
    When User opens intermediate CA with name E2e-test CA details
    Then Intermediate CA details are visible
