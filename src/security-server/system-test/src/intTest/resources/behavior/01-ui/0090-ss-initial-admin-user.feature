@SecurityServer
@UI
@InitialAdminUser
Feature: 0090 - SS: Initial admin user creation

  Scenario: Bootstrap view is shown when navigating to login URL
    Given SecurityServer login page is open
    Then Initial admin user creation form is visible

  Scenario: Bootstrap view is shown when navigating to an arbitrary URL
    Given SecurityServer page /clients is open
    Then Initial admin user creation form is visible

  Scenario: Bootstrap view is shown when navigating directly to admin user URL
    Given SecurityServer page /initial-admin-user is open
    Then Initial admin user creation form is visible

  Scenario: Weak password is rejected
    Given SecurityServer page /initial-admin-user is open
    And Initial admin user creation form is visible
    When Initial admin username is set to xrd
    And Initial admin password is set to secret
    And Initial admin password confirmation is set to secret
    And Initial admin user creation is submitted
    Then Initial admin user creation form shows weak password error

  Scenario: Password confirmation mismatch blocks submission
    Given SecurityServer page /initial-admin-user is open
    And Initial admin user creation form is visible
    When Initial admin username is set to xrd
    And Initial admin password is set to secret123!
    And Initial admin password confirmation is set to different456!
    Then Initial admin user creation submit button is disabled

  Scenario: Strong password succeeds and redirects to login
    Given SecurityServer page /initial-admin-user is open
    And Initial admin user creation form is visible
    When Initial admin username is set to xrd
    And Initial admin password is set to secret123!
    And Initial admin password confirmation is set to secret123!
    And Initial admin user creation is submitted
    Then Login form is visible
