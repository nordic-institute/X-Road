@SecurityServer
@Login
Feature: 0700 - SS: Permissions

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested

  Scenario: System administrator sees only relevant pages
    Given Login form is visible
    When User xrd-sys logs in to SecurityServer with password secret
    * Clients Tab is missing
    * Settings Tab is present
    * Diagnostics Tab is present
    * Keys and Certificates Tab is present

  Scenario: Registration officer sees only relevant pages
    Given Login form is visible
    When User xrd-reg logs in to SecurityServer with password secret
    Then Clients Tab is missing
    * Settings Tab is missing
    * Diagnostics Tab is missing
    * Add clients button is present
    * Client "TestGov" is opened
    * Client Details is as follows: Member Name "TestGov", Member Class "GOV", Member Code "0245437-2", Sign Cert: "X-Road Test CA CN"

    * Keys and certificates tab is selected
    * Token: softToken-0 is present
    * User cannot log-in or log-out out of token softToken-0
    * Security Server TLS Key sub-tab is selected
    * Generate TLS key is missing
    * Export TLS key is present
    * Api Keys Sub-Tab is missing

  Scenario: Security officer sees only relevant pages
    Given Login form is visible
    When User xrd-sec logs in to SecurityServer with password secret
    Then Clients Tab is missing
    * Settings Tab is present
    * Diagnostics Tab is missing
    * Add clients button is missing
    * Client "TestOrg" details are not available

    * Keys and certificates tab is selected
    * Token: softToken-0 is present
    * User can log-out out of token softToken-0
    * Security Server TLS Key sub-tab is selected
    * Generate TLS key is present
    * Export TLS key is present
    * Api Keys Sub-Tab is missing
    * Settings tab is selected
    * Backup and Restore Sub-Tab is missing

  Scenario: Observer sees only relevant pages
    Given Login form is visible
    When User xrd-obs logs in to SecurityServer with password secret
    Then Clients Tab is missing
    * Settings Tab is present
    * Diagnostics Tab is present
    * Add clients button is missing

    * Client "TestService" is opened
    * Client Details is as follows: Member Name "TestGov", Member Class "GOV", Member Code "0245437-2", Sign Cert: "X-Road Test CA CN"

    * Local groups sub-tab is selected
    * Add Local group button is missing
    * Local group "group-1" is selected
    * Local group details are read-only

    * Keys and certificates tab is selected
    * Token: softToken-0 is present
    * User cannot log-in or log-out out of token softToken-0
    * Token: softToken-0 - Add key is missing
    * Security Server TLS Key sub-tab is selected
    * Generate TLS key is missing
    * Export TLS key is missing
    * Api Keys Sub-Tab is present
    * Settings tab is selected
    * Backup and Restore Sub-Tab is missing
    * Configuration Anchor buttons are missing

  Scenario: Service administrator sees only relevant pages
    Given Login form is visible
    When User xrd-ser logs in to SecurityServer with password secret
    Then Clients Tab is missing
    * Settings Tab is missing
    * Diagnostics Tab is missing
    * Keys and Certificates Tab is missing
    * Add clients button is missing

    * Client "TestService" is opened
    * Client Details is as follows: Member Name "TestGov", Member Class "GOV", Member Code "0245437-2", Sign Cert: "X-Road Test CA CN"

    * Local groups sub-tab is selected
    * Add Local group button is present
    * Local group "group-1" is selected
    * Local group details are editable

