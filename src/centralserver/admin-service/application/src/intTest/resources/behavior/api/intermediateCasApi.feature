@IntermediateCas
Feature: Intermediate CAS API

  Background:
    Given Certification service is created

  Scenario: Adding OCSP responder to intermediate CAS
    Given intermediate CAS added to certification service
    Then OCSP responder is added to intermediate CAS

  Scenario: View the OCSP responders of an intermediate CA
    Given intermediate CAS added to certification service
    And OCSP responder is added to intermediate CAS
    And OCSP responder is added to intermediate CAS
    And OCSP responder is added to intermediate CAS
    Then intermediate CA has 3 OCSP responders

  Scenario: Modify the OCSP responder of intermediate CA
    Given intermediate CAS added to certification service
    And OCSP responder is added to intermediate CAS
    When OCSP responder url is updated
    Then intermediate CA has 1 OCSP responders
    And intermediate CA has the updated OCSP responder

  Scenario: Modify the OCSP responder of intermediate CA #2
    Given intermediate CAS added to certification service
    And OCSP responder is added to intermediate CAS
    When OCSP responder url and certificate is updated
    Then intermediate CA has the updated OCSP responder
    And the OCSP responder certificate was updated
