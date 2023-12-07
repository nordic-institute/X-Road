@CentralServer
@CertificationService
@IntermediateCA
Feature: 0550 - CS: Trust Services -> CA Details -> Intermediate CAs -> Intermediate CA OCSP Responders

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

    When TrustServices tab is selected
    And new certification service is added
    And user opens certification service details
    And Intermediate CAs tab is selected
    And Intermediate CA with name E2e-test CA is added
    And Intermediate CA with name E2e-test2 CA is added
    And User opens intermediate CA with name E2e-test CA details
    And Intermediate CA OCSP responders tab is selected

  Scenario: Intermediate CA OCSP responder can be added
    When OCSP responder with URL "http://e2e-test-ocsp-responder.com" is added
    And  OCSP responder with URL "http://e2e-test-ocsp-responder-cert.com" and random cert is added
    Then OCSP responder with URL http://e2e-test-ocsp-responder.com is visible in the OCSP responders list
    And OCSP responder with URL http://e2e-test-ocsp-responder-cert.com is visible in the OCSP responders list

  Scenario: Intermediate CA OCSP responders list is correctly shown
    When OCSP responder table is visible
    And OCSP responder with URL "http://e2e-test-ocsp-responder.com" and random cert is added
    Then User is able to sort OCSP responders by URL
    And User is able to view the certificate of OCSP responder with URL http://e2e-test-ocsp-responder.com

  Scenario: Intermediate CA OCSP responder can be edit in list
    When OCSP responder with URL "http://e2e-test-ocsp-responder.com" and random cert is added
    And User is able click Edit button in OCSP responder with URL http://e2e-test-ocsp-responder.com
    Then User is able view the certificate of OCSP responder
    When User is able click Edit button in OCSP responder with URL http://e2e-test-ocsp-responder.com
    Then User is able change the certificate of OCSP responder with URL http://e2e-test-ocsp-responder.com
    When User is able click Edit button in OCSP responder with URL http://e2e-test-ocsp-responder.com
    And User is able change the URL to new URL http://new-e2e-test-ocsp-responder.com
    Then OCSP responder with URL http://new-e2e-test-ocsp-responder.com is visible in the OCSP responders list

  Scenario: Intermediate CA OCSP responder can be delete in list
    When OCSP responder with URL "http://e2e-test-ocsp-responder.com" is added
    Then User is able to click delete button in OCSP responder with URL http://e2e-test-ocsp-responder.com
    And OCSP responder with URL http://e2e-test-ocsp-responder.com should removed in list
