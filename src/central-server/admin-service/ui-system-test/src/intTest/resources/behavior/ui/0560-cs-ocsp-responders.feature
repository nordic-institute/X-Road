@CentralServer
@CertificationService
@This
Feature: 0560 - CS: Trust Services -> CA Details -> OCSP Responders

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

    When TrustServices tab is selected
    And new certification service is added
    And user opens certification service details
    And OCSP responders tab is selected

  Scenario: OCSP responder can be added
    When OCSP responder with URL http://e2e-test-ocsp-responder.com is added
    Then OCSP responder with URL http://e2e-test-ocsp-responder.com is visible in the OCSP responders list

  Scenario: OCSP responders list is correctly shown
    When OCSP responder table is visible
    And OCSP responder with URL http://e2e-test-ocsp-responder.com is added
    Then User is able to sort OCSP responders by URL
    And User is able to view the certificate of OCSP responder with URL http://e2e-test-ocsp-responder.com

  Scenario: OCSP responder can be edit in list
    When OCSP responder with URL http://e2e-test-ocsp-responder.com is added
    And User is able click Edit button in OCSP responder with URL http://e2e-test-ocsp-responder.com
    Then User is able view the certificate of OCSP responder
    When User is able click Edit button in OCSP responder with URL http://e2e-test-ocsp-responder.com
    Then User is able change the certificate of OCSP responder with URL http://e2e-test-ocsp-responder.com
    When User is able click Edit button in OCSP responder with URL http://e2e-test-ocsp-responder.com
    And User is able change the URL to new URL http://new-e2e-test-ocsp-responder.com
    Then OCSP responder with URL http://new-e2e-test-ocsp-responder.com is visible in the OCSP responders list

  Scenario: OCSP responder can be delete in list
    When OCSP responder with URL http://e2e-test-ocsp-responder.com is added
    Then User is able to click delete button in OCSP responder with URL http://e2e-test-ocsp-responder.com
    And OCSP responder with URL http://e2e-test-ocsp-responder.com should removed in list
