@Modifying
@IntermediateCas
Feature: Intermediate CAS API

  Background:
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service is created

  Scenario: Adding intermediate CA is created
    Given intermediate CA added to certification service
    When intermediate CAs are retrieved
    Then intermediate CA is as follows
      | #$hash                    | [not_null]  |
      | $issuerDistinguishedName  | EMAILADDRESS=aaa@bbb.ccc, CN=Cyber, OU=ITO, O=Cybernetica, C=EE   |
      | $subjectDistinguishedName | CN=Subject  |
      | $subjectCommonName        | Subject     |
      | $notBefore                | [generated] |
      | $notAfter                 | [generated] |

  Scenario: Adding intermediate CA is deleted
    Given intermediate CA added to certification service
    When intermediate CA is deleted
    Then deleted intermediate CA is not present

  Scenario: Adding OCSP responder to intermediate CA
    Given intermediate CA added to certification service
    Then OCSP responder is added to intermediate CA

  Scenario: View the OCSP responders of an intermediate CA
    Given intermediate CA added to certification service
    And OCSP responder is added to intermediate CA
    And OCSP responder is added to intermediate CA
    And OCSP responder is added to intermediate CA
    Then intermediate CA has 3 OCSP responders

  Scenario: Deleting OCSP responder from intermediate CA
    Given intermediate CA added to certification service
    And OCSP responder is added to intermediate CA
    And intermediate CA has 1 OCSP responders
    When OCSP responder is deleted from intermediate CA
    Then intermediate CA has 0 OCSP responders

  Scenario: Deleting OCSP responder from intermediate CA #2
    Given intermediate CA added to certification service
    And OCSP responder is added to intermediate CA
    And intermediate CA has 1 OCSP responders
    When OCSP responder is deleted by id
    Then intermediate CA has 0 OCSP responders

  Scenario: Modify the OCSP responder of intermediate CA
    Given intermediate CA added to certification service
    And OCSP responder is added to intermediate CA
    When OCSP responder url is updated
    Then intermediate CA has 1 OCSP responders
    And intermediate CA has the updated OCSP responder

  Scenario: Modify the OCSP responder of intermediate CA #2
    Given intermediate CA added to certification service
    And OCSP responder is added to intermediate CA
    When OCSP responder url and certificate is updated
    Then intermediate CA has the updated OCSP responder
    And the OCSP responder certificate was updated
