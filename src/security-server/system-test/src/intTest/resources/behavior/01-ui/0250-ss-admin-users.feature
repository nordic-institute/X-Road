Feature: 0250 - SS: Admin Users
  The admin users management feature should be accessible when database-based authentication is enabled

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Settings tab is selected
    And Admin Users sub-tab is selected

  Scenario: User can add new admin user with all roles
    Given Admin Users table has 1 entries
    When Add Admin Users wizard is opened
    And Role "Security Officer" is being checked in the wizard
    And Role "Registration Officer" is being checked in the wizard
    And Role "Service Administrator" is being checked in the wizard
    And Role "System Administrator" is being checked in the wizard
    And Role "Server Observer" is being checked in the wizard
    And Wizard's Next button is clicked
    And Username test is entered
    And Password secret is entered
    And Confirmation password secret is entered
    And Wizard's Save button is clicked
    Then Admin user test is present in the list and has roles
      | $role                 | $condition |
      | Security Officer      | present    |
      | Registration Officer  | present    |
      | Service Administrator | present    |
      | System Administrator  | present    |
      | Server Observer       | present    |
    And Admin Users table has 2 entries

  Scenario: User can edit existing admin user's roles
    When Admin user test's edit dialog is opened
    And Role "Security Officer" is being checked
    And Role "Service Administrator" is being checked
    And Role "Server Observer" is being checked
    And Dialog Save button is clicked
    Then Admin user test is present in the list and has roles
      | $role                 | $condition |
      | Security Officer      | missing    |
      | Registration Officer  | present    |
      | Service Administrator | missing    |
      | System Administrator  | present    |
      | Server Observer       | missing    |

  Scenario: User can only assign roles they have when adding/editing admin user
    Given logout button is being clicked
    And SecurityServer login page is open
    And Page is prepared to be tested
    And User test logs in to SecurityServer with password secret
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

  Scenario: User can change existing user's password
    When Admin user test's password change dialog is opened
    And Old password secret is entered
    And New password secret2 is entered
    And New password's confirmation secret2 is entered
    And Change password dialog's Save button is clicked
    And logout button is being clicked
    And SecurityServer login page is open
    Then User test logs in to SecurityServer with password secret
    And Error message for incorrect credentials is shown
    And Login form is visible
    And User test logs in to SecurityServer with password secret2
    And logout button is being clicked

  Scenario: User can delete existing admin user
    Given Admin Users table has 2 entries
    When Admin user test is deleted
    Then Admin Users table has 1 entries
    And Admin user test is not present in the list

  Scenario Outline: Add necessary admin users for other dependant tests
    When Add Admin Users wizard is opened
    And Role "<$role>" is being checked
    And Wizard's Next button is clicked
    And Username <$username> is entered
    And Password <$password> is entered
    And Confirmation password <$password> is entered
    And Wizard's Save button is clicked
    Then Admin user <$username> is present in the list and has roles
      | $role   | $condition |
      | <$role> | present    |
    Examples:
      | $username | $role                 | $password |
      | xrd-sec   | Security Officer      | secret    |
      | xrd-reg   | Registration Officer  | secret    |
      | xrd-ser   | Service Administrator | secret    |
      | xrd-sys   | System Administrator  | secret    |
      | xrd-obs   | Server Observer       | secret    |
