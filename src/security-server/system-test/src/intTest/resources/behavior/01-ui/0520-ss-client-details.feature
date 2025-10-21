@SecurityServer
@Client
@Skip #TODO beta1 release preparation
Feature: 0520 - SS: Client Details

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Client details are displayed
    When Client "Test service" is opened
    Then Client Details is as follows: Member Name "Test member", Member Class "COM", Member Code "1234", Sign Cert: "Test CA"
    When Client sign certificate "Test CA" is selected
    Then Certificate fields as follows:
      | Version                    | 3                                                  |
      | Signature Algorithm        | SHA256withRSA                                      |
      | Issuer Distinguished Name  | CN=Test CA, O=X-Road Test CA                       |
      | Subject Distinguished Name | SERIALNUMBER=DEV/SS0/COM, CN=1234, O=ui-test, C=FI |
    And Certificate is closed

  Scenario: Client Disable button is clicked
    When Client "Test service" is opened
    When Client Disable button is clicked
    Then error: "Sending of management request failed" was displayed

  Scenario: Subsystem rename allowed multiple times on saved client
    Given Client with id: "DEV:COM:1234:named-random-sub-3" is opened
    And Subsystem is rename status is: 'Name change will be applied on client registration'
    When Client Edit button is clicked
    Then Rename dialog save button is disabled
    When Subsystem name is set to ""
    Then Rename dialog save button is disabled
    When Subsystem name is set to "Updated1"
    Then Rename dialog save button is active
    When Dialog data is saved and success message 'Subsystem name change successfully added and will be applied on client registration' is shown
    Then Subsystem is rename status is: 'Name change will be applied on client registration'
    When Client Edit button is clicked
    Then Rename dialog save button is disabled
    When Subsystem name is set to "Updated2"
    Then Rename dialog save button is active
    When Dialog data is saved and success message 'Subsystem name change successfully added and will be applied on client registration' is shown
    Then Subsystem is rename status is: 'Name change will be applied on client registration'

  Scenario: Subsystem rename request is sent imidiately
    Given Client "Test service" is opened
    And Subsystem is rename status is: 'Name change will be applied on client registration'
    When Client Edit button is clicked
    Then Rename dialog save button is disabled
    When Subsystem name is set to ""
    Then Rename dialog save button is disabled
    When Subsystem name is set to "Updated1"
    Then Rename dialog save button is active
    Then Dialog data is saved and error message 'Sending of management request failed' is shown
    And Subsystem is rename status is: 'Name change will be applied on client registration'
