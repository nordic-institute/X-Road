@SecurityServer
@ApiKeys
Feature: 0350 - SS: API Keys

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Keys and certificates tab is selected
    And API Keys sub-tab is selected

  Scenario: User can create API key with all privileges
    Given Create API key button is clicked
    And Create API key wizard next button status is disabled
    When Role "Security Officer" is being clicked
    When Role "Registration Officer" is being clicked
    When Role "Service Administrator" is being clicked
    When Role "System Administrator" is being clicked
    When Role "Server Observer" is being clicked
    Then Create API key wizard next button status is enabled

    When Create API key wizard next button is clicked
    And Create API key wizard Previous button is clicked
    And Create API key wizard next button is clicked
    And Create API key wizard Create Key button is clicked
    Then API key is created and visible

    When Create API key wizard Finish button is clicked
    Then Newly created API key is present in the list

  Scenario: API key is created and revoked
    Given Create API key button is clicked
    When Role "Registration Officer" is being clicked
    When Create API key wizard next button is clicked
    And Create API key wizard Create Key button is clicked
    Then API key is created and visible
    When Create API key wizard Finish button is clicked
    Then Newly created API key is present in the list
    When Newly created API key is revoked
    Then Newly created API key is missing in the list

  Scenario: API key is created and edited
    Given Create API key button is clicked
    When Role "Registration Officer" is being clicked
    When Create API key wizard next button is clicked
    And Create API key wizard Create Key button is clicked
    Then API key is created and visible
    When Create API key wizard Finish button is clicked
    Then Newly created API key is present in the list
    When Newly created API key is edit dialog is opened
    And Role "Security Officer" is being clicked
    And Role "Registration Officer" is being clicked
    And Role "Service Administrator" is being clicked
    And Role "System Administrator" is being clicked
    And Role "Server Observer" is being clicked
    And Dialog Save button is clicked
    Then Newly created API key is present in the list and has roles
      | $role                 | $condition |
      | Security Officer      | present    |
      | Registration Officer  | missing    |
      | Service Administrator | present    |
      | System Administrator  | present    |
      | Server Observer       | present    |

  Scenario: User can only assign roles they have when creating/editing API key
    Given Create API key button is clicked
    And Role "Security Officer" is being clicked
    And Role "Registration Officer" is being clicked
    And Role "Service Administrator" is being clicked
    And Role "System Administrator" is being clicked
    And Role "Server Observer" is being clicked
    And Create API key wizard next button is clicked
    And Create API key wizard Create Key button is clicked
    Then API key is created and visible
    When Create API key wizard Finish button is clicked
    And Newly created API key is present in the list
    And logout button is being clicked

    When SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd-sys logs in to SecurityServer with password secret
    And API Keys sub-tab is selected
    When Newly created API key is edit dialog is opened
    And Role "Security Officer" is being clicked
    And Role "Registration Officer" is being clicked
    And Role "Service Administrator" is being clicked
    And Role "Server Observer" is being clicked
    And Dialog Save button is clicked
    Then Newly created API key is present in the list and has roles
      | $role                 | $condition |
      | Security Officer      | missing    |
      | Registration Officer  | missing    |
      | Service Administrator | missing    |
      | System Administrator  | present    |
      | Server Observer       | missing    |
    When Newly created API key is edit dialog is opened
    Then Role "Security Officer" is not available
    Then Role "Registration Officer" is not available
    Then Role "Service Administrator" is not available
    And Role "Server Observer" is not available
