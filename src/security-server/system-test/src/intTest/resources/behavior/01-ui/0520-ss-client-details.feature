@SecurityServer
@Client
Feature: 0520 - SS: Client Details

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Client details are displayed
    When Client "TestService" is opened
    Then Client Details is as follows: Member Name "Test member", Member Class "COM", Member Code "1234", Sign Cert: "Test CA"
    When Client sign certificate "Test CA" is selected
    Then Certificate fields as follows:
      | Version                    | 3                                                  |
      | Signature Algorithm        | SHA256withRSA                                      |
      | Issuer Distinguished Name  | CN=Test CA, O=Test                                 |
      | Subject Distinguished Name | SERIALNUMBER=DEV/SS0/COM, CN=1234, O=ui-test, C=FI |
    And Certificate is closed

  Scenario: Client Disable button is clicked
    Given Client "TestService" is opened
    When Client Disable button is clicked
    Then error: "Sending of management request failed" was displayed
