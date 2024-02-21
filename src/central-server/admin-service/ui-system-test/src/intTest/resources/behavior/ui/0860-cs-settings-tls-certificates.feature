@CentralServer
Feature: 0860 - CS: Settings -> TLS Certificates

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected

 Scenario: Management Service TLS certificate is visible and all buttons are enabled
   Given TLS Certificates sub-tab is selected
   And Management Service TLS key hash is visible
   And Downloading certificate button is enabled
   And Re-create key button is enabled
   And Generate CSR is enabled
   And Upload certificate is enabled

  Scenario: Management Service TLS certificate can be viewed
    Given TLS Certificates sub-tab is selected
    When Management Service TLS key hash field is clicked
    Then user is able to view the certificate details

  Scenario: Management Service TLS certificate can be downloaded
    Given TLS Certificates sub-tab is selected
    When Downloading certificate button is clicked
    Then Management Service certificate is successfully downloaded

  Scenario: Management Service TLS key and certificate can be re-created
    Given TLS Certificates sub-tab is selected
    When Re-create key button is clicked
    Then new key and certificate are successfully created

  Scenario: Management Service TLS certificate sign request can be generated
    Given TLS Certificates sub-tab is selected
    When Generate CSR button is clicked
    Then new dialog is opened and Enter Distinguished name is asked and value CN=cs is entered
    When dialog Generate CSR button is clicked
    Then generated sign request is downloaded

  Scenario: Management Service TLS certificate with different key can't be uploaded
    Given TLS Certificates sub-tab is selected
    When different management service TLS certificate management-service-new.crt is uploaded
    Then error: "The imported certificate does not match the TLS key" was displayed
