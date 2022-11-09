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

  Scenario: Deleting OCSP responder from intermediate CAS
    Given Certification service is created
    And intermediate CAS added to certification service
    And OCSP responder is added to intermediate CAS
    Then OCSP responder is deleted from intermediate CAS