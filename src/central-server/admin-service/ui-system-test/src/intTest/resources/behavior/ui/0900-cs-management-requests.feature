@CentralServer
@ManagementRequests
Feature: CS: Management Requests

  Background:
    Given CentralServer login page is open
    Then User xrd logs in to CentralServer with password secret
    # API key creation
    When CentralServer Settings tab is selected
    And API Keys sub-tab is selected
    And Create API key button is clicked
    And Role "Registration Officer" is being clicked
    And Create API key wizard next button is clicked
    And Create API key wizard Create Key button is clicked
    And API key is created and visible
    Then Get API key and set REGISTRATION_OFFICER to Authentication header
    And Create API key wizard Finish button is clicked
    # API key creation END
    And Management requests tab is selected

  @Modifying
  Scenario: When Management requests are created then user can approve a request
    When New security server E2E-SS1 authentication certificate registered with owner code e2e-tc1-member-subsystem
    And Show only pending requests is checked
    Then User is able to view the Management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    And User is able click Approve button in row from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    Then Management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem should removed in list

  @Modifying
  Scenario: When Management requests are created then user can declined a request
    When New security server E2E-SS2 authentication certificate registered with owner code e2e-tc2-member-subsystem
    And Show only pending requests is checked
    Then User is able to view the Management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem
    And User is able click Decline button in row from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem
    Then Management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem should removed in list

  @Modifying
  Scenario: The user can view the details of pending management request and can Approve request
    When Client with code e2e-tc2-member-subsystem is registered in security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    And Show only pending requests is checked
    Then User is able to click Pending Management request Add Client from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    And The details page is shown with title Add Client
    And PENDING Add Client details page contains details about the Request Information
    And Add Client details page contains information about the Affected Security Server Information
    And The details page show client information about the Client Submitted for Registration
    And User is able click Approve button
    Then Management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem should removed in list

  @Modifying
  Scenario: The user can view the details of pending management request and can Decline request
    When New security server E2E-SS3 authentication certificate registered with owner code e2e-tc3-member-subsystem
    And Show only pending requests is checked
    Then User is able to click Pending Management request Add Certificate from Security server E2E-SS3 with owner code e2e-tc3-member-subsystem
    And The details page is shown with title Add Certificate
    And PENDING Add Certificate details page contains details about the Request Information
    And Add Certificate details page contains information about the Affected Security Server Information
    And The details page show certificate information about the Authentication Certificate Submitted for Registration
    And User is able click Decline button
    Then Management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem should removed in list
    And The user can not see the Approve, Decline actions for requests that have already been processed

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
    Then User is able to view the Management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem
