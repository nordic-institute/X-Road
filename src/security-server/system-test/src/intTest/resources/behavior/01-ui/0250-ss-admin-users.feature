@SecurityServer
@UI
Feature: 0250 - SS: Admin Users
  The admin users management feature should be accessible when database-based authentication is enabled

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Settings tab is selected
    And Admin Users sub-tab is selected

  Scenario: Too weak password is not accepted when adding new user
    When Add Admin Users wizard is opened
    And Role "Server Observer" is being checked in the wizard
    And Wizard's Next button is clicked
    And Username test is entered
    And Password t0pSecret is entered
    And Confirmation password t0pSecret is entered
    Then Wizard's Save button is clicked and error: "The provided password was too weak" is displayed

  Scenario: Password containing illegal characters is not accepted when adding new user
    When Add Admin Users wizard is opened
    And Role "Server Observer" is being checked in the wizard
    And Wizard's Next button is clicked
    And Username test is entered
    And Password t0pSecretä is entered
    And Confirmation password t0pSecretä is entered
    Then Wizard's Save button is clicked and error: "The provided password contains invalid characters" is displayed

  Scenario: User can only assign roles they have when adding/editing admin user
    Given logout button is being clicked
    And SecurityServer login page is open
    And Page is prepared to be tested
    And User test logs in to SecurityServer with password t0pSecret1
    And Settings tab is selected
    And Admin Users sub-tab is selected
    When Add Admin Users wizard is opened
    Then Role "Security Officer" should not be visible in the wizard
    And Role "Registration Officer" should be visible in the wizard
    And Role "Service Administrator" should not be visible in the wizard
    And Role "System Administrator" should be visible in the wizard
    And Role "Server Observer" should not be visible in the wizard
    And Wizard's Cancel button is clicked
    When Admin user test's edit dialog is opened
    Then Role "Security Officer" should not be visible
    And Role "Registration Officer" should be visible
    And Role "Service Administrator" should not be visible
    And Role "System Administrator" should be visible
    And Role "Server Observer" should not be visible
    And Dialog is closed

  Scenario: Too weak password is not accepted when changing other user's password
    When Admin user test's password change dialog is opened
    And Old password input is not visible
    And New password t0pSecret is entered
    And New password's confirmation t0pSecret is entered
    Then Change password dialog's Save button is clicked and error: "The provided password was too weak" is displayed

  Scenario: Password containing illegal characters is not accepted when changing user's password
    When Admin user test's password change dialog is opened
    And Old password input is not visible
    And New password t0pSecretä is entered
    And New password's confirmation t0pSecretä is entered
    Then Change password dialog's Save button is clicked and error: "The provided password contains invalid characters" is displayed

  Scenario: Too weak password is not accepted when changing own password
    When Change password button is being clicked
    And Old password secret123! is entered
    And New password t0pSecret is entered
    And New password's confirmation t0pSecret is entered
    Then Change password dialog's Save button is clicked and error: "The provided password was too weak" is displayed

  Scenario: Password containing illegal characters is not accepted when changing own password
    When Change password button is being clicked
    And Old password secret123! is entered
    And New password t0pSecretä is entered
    And New password's confirmation t0pSecretä is entered
    Then Change password dialog's Save button is clicked and error: "The provided password contains invalid characters" is displayed
