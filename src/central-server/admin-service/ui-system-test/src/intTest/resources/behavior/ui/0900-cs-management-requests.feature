@CentralServer
@ManagementRequests
Feature: CS: Management Requests

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected
    And API Keys sub-tab is selected
    Given Create API key button is clicked
    When Role "Registration Officer" is being clicked
    When Create API key wizard next button is clicked
    And Create API key wizard Create Key button is clicked
    Then API key is created and visible
    Then Get API key
    And Authentication header is set to REGISTRATION_OFFICER
    When Create API key wizard Finish button is clicked
    And Management requests tab is selected

  @Modifying
  Scenario: API key is created and Authentication header is set and Management requests are created
    And New security server CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:E2E-SS2 authentication certificate registered
    And New security server CS-E2E:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1 authentication certificate registered

  @Modifying
  Scenario: The user can approve a request
    When Show only pending requests is checked
    And User is able to view the Management request with Security server Identifier CS-E2E:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1
    And User is able click Approve button in row with Security server Identifier CS-E2E:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1
    Then Management request with Security server Identifier CS-E2E:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1 should removed in list

  @Modifying
  Scenario: The user can declined a request
    When Show only pending requests is checked
    And User is able to view the Management request with Security server Identifier CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:E2E-SS2
    And User is able click Decline button in row with Security server Identifier CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:E2E-SS2
    Then Management request with Security server Identifier CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:E2E-SS2 should removed in list

  @Modifying
  Scenario: The user can view the details of pending management request and can Approve request
    When Client CS-E2E:E2E-TC1:e2e-tc2-member-subsystem is registered as security server CS-E2E:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1
    And Show only pending requests is checked
    Then User is able to click Pending Management request Add Client with Security server CS-E2E:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1
    And The details page is shown with title Add Client
    And PENDING Add Client details page contains details about the Request Information
    And Add Client details page contains information about the Affected Security Server Information
    And The details page show client information about the Client Submitted for Registration
    And User is able click Approve button
    Then Management request with Security server Identifier CS-E2E:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1 should removed in list

  @Modifying
  Scenario: The user can view the details of pending management request and can Decline request
    When New security server CS-E2E:E2E-TC1:e2e-tc3-member-subsystem:E2E-SS3 authentication certificate registered
    And Show only pending requests is checked
    Then User is able to click Pending Management request Add Certificate with Security server CS-E2E:E2E-TC1:e2e-tc3-member-subsystem:E2E-SS3
    And The details page is shown with title Add Certificate
    And PENDING Add Certificate details page contains details about the Request Information
    And Add Certificate details page contains information about the Affected Security Server Information
    And The details page show certificate information about the Authentication Certificate Submitted for Registration
    And User is able click Decline button
    Then Management request with Security server Identifier CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:E2E-SS2 should removed in list
    Then The user can not see the Approve, Decline actions for requests that have already been processed

  Scenario: The table correctly shows management requests
    When Management Requests table with columns Id, Created, Type, Server Owner Name, Server Identifier, Status is visible
    Then User is able to sort the table by column 1
    And User is able to sort the table by column 2
    And User is able to sort the table by column 3
    And User is able to sort the table by column 4
    And User is able to sort the table by column 5
    And User is able to sort the table by column 6

  Scenario: The table can be search by the free text field based on visible columns
    When e2e-tc2 is written in table search field
    Then User is able to view the Management request with Security server Identifier CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:E2E-SS2
