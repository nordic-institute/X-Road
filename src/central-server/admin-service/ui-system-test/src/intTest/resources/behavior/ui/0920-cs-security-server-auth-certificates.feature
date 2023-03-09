@CentralServer
@SecurityServers
Feature: CS: Security server: Clients

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Security Servers tab is selected

  Scenario: Security server authentication certificates for "SS-X" are listed
    Given user opens security server details for "SS-X"
    And navigates to security server authentication certificates tab
    Then A authentication certificate with authority name: X-Road test, serial number: 12 & subject: /C=/FI/O=NIIS/CN=xroad-lxd-ss1 is listed
    And A authentication certificate with authority name: Test CA CN, serial number: 4 & subject: /C=/FI/O=NIIS/CN=xroad-lxd-ss3 is listed

  Scenario: Security server authentication certificates can be sorted
    Given user opens security server details for "SS-X"
    And navigates to security server authentication certificates tab
    Then user can sort certificates list by Certification Authority
    And user can sort certificates list by Serial Number
    And user can sort certificates list by Subject
    And user can sort certificates list by Expires


