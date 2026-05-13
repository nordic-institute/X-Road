@SecurityServer
@UI
@Initialization
Feature: 0400 - SS: System Parameters
  Verify that SS can be initialized from fresh state.

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password
    And Settings tab is selected
    And System Parameters sub-tab is selected

  Scenario: Security server address is update fails
    Given Security Server address is displayed
    And Security Server address edit button is enabled
    When Security Server address edit button is clicked
    And new Security Server address "new.address" is submitted
    Then error: "Sending of management request failed" was displayed

  Scenario: Timestamping service is selected and deleted
    Given Timestamping services table has 0 entries
    When Add Timestamping services dialog is opened
    When First timestamping option is selected
    Then Timestamping services table has 1 entries
    When Timestamping service on row 0 is deleted
    Then Timestamping services table has 0 entries

  Scenario: Timestamping service is selected
    Given Timestamping services table has 0 entries
    When Add Timestamping services dialog is opened
    And Add Timestamping services dialog is closed
    And Add Timestamping services dialog is opened
    And First timestamping option is selected
    Then  Timestamping services table has 1 entries
    And Timestamping services table row 0 has service "Test TSA" and url "http://testca:8899" and cost type "Free"
    And Timestamping prioritization strategy is "PAID_FIRST"

  Scenario: Approved CA component has correct values
    Then Approved CAs table row 0 has distinguished name "CN=Test CA, O=Test" and ocsp url "http://testca:8888" and ocsp cost type "Free"
    And Ocsp prioritization strategy is "ONLY_FREE"

  Scenario: Administrator cannot turn on maintenance mode for management services provider
    Then maintenance mode toggle is off
    And maintenance mode toggle is disabled

  Scenario: Configurable properties section is visible with scope panels
    Then Configurable properties panels are visible
    And Configurable properties panel for scope "proxy-ui-api" is present

  Scenario: Configurable properties panel can be expanded and shows property rows
    Given Configurable properties panel for scope "proxy-ui-api" is expanded
    Then Configurable property row "xroad.proxy-ui-api.rate-limit-requests-per-second" is visible in scope "proxy-ui-api"

  Scenario: Configurable property can be edited and restart warning is shown
    Given Configurable properties panel for scope "proxy-ui-api" is expanded
    When Edit button for property "xroad.proxy-ui-api.rate-limit-requests-per-second" in scope "proxy-ui-api" is clicked
    Then Edit configurable property dialog is open
    When Configurable property value is changed to "25"
    Then Dialog data is saved and success message "Property updated successfully" is shown
    And Configurable properties restart warning is visible
    And Configurable property "xroad.proxy-ui-api.rate-limit-requests-per-second" in scope "proxy-ui-api" has current value "25"

  Scenario: Edit configurable property dialog can be cancelled without changes
    Given Configurable properties panel for scope "proxy-ui-api" is expanded
    When Edit button for property "xroad.proxy-ui-api.rate-limit-requests-per-second" in scope "proxy-ui-api" is clicked
    Then Edit configurable property dialog is open
    When Edit configurable property dialog is cancelled
    Then Edit configurable property dialog is not visible
    And Configurable property "xroad.proxy-ui-api.rate-limit-requests-per-second" in scope "proxy-ui-api" has current value "25"

  Scenario: Configurable properties can be filtered by search term
    When Configurable properties search is used with "rate-limit-requests-per-second"
    Then Configurable properties panel for scope "proxy-ui-api" is present
    And Configurable property row "xroad.proxy-ui-api.rate-limit-requests-per-second" is visible in scope "proxy-ui-api"
