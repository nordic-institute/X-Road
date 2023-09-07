@CentralServer
@SecurityServers
@SecurityServerCertificates
Feature: 1020 - CS: Security server: Certificates

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Security Servers tab is selected
    And user opens security server details for "E2E-SS1"
    And navigates to security server authentication certificates tab

  Scenario Outline: Security server authentication certificates for "E2E-SS1" are listed
    Given A authentication certificate with authority name: <authority>, serial number: <serial> & subject: <subject> is listed
    When user clicks on certification authority: <authority>
    Then user can see certificate details
    Examples:
      | authority            | serial | subject              |
      | Subject-E2e-test2 CA | 2      | CN=Subject-2-E2E-SS1 |
      | Subject-E2e-test CA  | 1      | CN=Subject-E2E-SS1   |

  Scenario: Security server authentication certificates can be sorted
    Given user can sort certificates list by Certification Authority
    And user can sort certificates list by Serial Number
    And user can sort certificates list by Subject
    And user can sort certificates list by Expires

  Scenario: Security server authentication certificate can be deleted
    Given authentication certificates list contains 2 items
    When user opens delete dialog for first authentication certificate in list
    Then user cannot delete Authentication certificate
    And closes delete Authentication certificate dialog
    When user opens delete dialog for first authentication certificate in list
    When enters server code: "incorrect-code" to delete Authentication certificate
    Then user cannot delete Authentication certificate
    When enters server code: "E2E-SS1" to delete Authentication certificate
    And deletes Authentication certificate
    Then authentication certificates list contains 1 items

