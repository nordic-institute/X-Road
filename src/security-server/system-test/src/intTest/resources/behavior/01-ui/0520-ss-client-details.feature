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
    Then Client Details is as follows: Member Name "TestGov", Member Class "GOV", Member Code "0245437-2", Sign Cert: "X-Road Test CA CN"
    When Client sign certificate "X-Road Test CA CN" is selected
    Then Certificate fields as follows:
      | Version                    | 3                                                               |
      | Signature Algorithm        | SHA256withRSA                                                   |
      | Issuer Distinguished Name  | CN=X-Road Test CA CN, OU=X-Road Test CA OU, O=X-Road Test, C=FI |
      | Subject Distinguished Name | SERIALNUMBER=CS/SS1/GOV, CN=0245437-2, O=ui-test, C=FI          |
    And Certificate is closed

  Scenario: Client Disable button is clicked
    Given Client "TestService" is opened
    When Client Disable button is clicked
    Then error: "Sending of management request failed" was displayed
