@CentralServer
@ManagementRequests
Feature: 0900 - CS: Management Requests

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
    Then API key is set to token REGISTRATION_OFFICER and in Authentication header
    And Create API key wizard Finish button is clicked
    # API key creation END

  Scenario: User Approves Management Request
    Given a new security server E2E-SS1 with authentication certificate is registered with owner code e2e-tc1-member-subsystem
    And Management requests tab is selected
    And the option to show only pending requests is selected
    When the user views the Management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    And the user clicks on the Approve button in the row from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    Then the pending management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem should be removed from the list

  Scenario: User Declines Management Request
    Given a new security server E2E-SS2 with authentication certificate is registered with owner code e2e-tc2-member-subsystem
    And Management requests tab is selected
    And the option to show only pending requests is selected
    When the user views the Management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem
    And the user clicks on the Decline button in the row from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem
    Then the pending management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem should be removed from the list

  Scenario: User views Details and Approves pending Management Request
    Given a client with code e2e-tc2-member-subsystem and subsystem code e2e-tc2-subsystem is registered in security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    And Management requests tab is selected
    And the option to show only pending requests is selected
    When the user clicks Pending request Add Client from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    Then the details page is shown with title Add Client
    And the details page displays the Request Information for the PENDING management request
    And the details page displays the Affected Security Server Information for the Client request
    And the details page displays the client information for the Client Submitted for Registration
    When the user clicks Approve button
    Then the pending management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem should be removed from the list

  Scenario: User views Details and Decline pending Management Request
    Given a new security server E2E-SS3 with authentication certificate is registered with owner code e2e-tc3-member-subsystem
    And Management requests tab is selected
    And the option to show only pending requests is selected
    When the user clicks Pending request Add Certificate from Security server E2E-SS3 with owner code e2e-tc3-member-subsystem
    And the details page is shown with title Add Certificate
    And the details page displays the Request Information for the PENDING management request
    And the details page displays the Affected Security Server Information for the Certificate request
    And the details page show certificate information about the Authentication Certificate Submitted for Registration
    When the user clicks Decline button
    Then the user can not see the Approve, Decline actions for requests that have already been processed
    And the pending management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem should be removed from the list

  Scenario: Verify sorting functionality in Management Requests table
    Given Management requests tab is selected
    And the Management Requests table should be visible
    Then the default sort is by Id descending
    And user is able to sort the table by field Created
    And user is able to sort the table by field Type
    And user is able to sort the table by field Server Owner Name
    And user is able to sort the table by field Server Identifier
    And user is able to sort the table by field Status

  Scenario: Search for pending Management Requests based on Free Text in Visible Columns
    Given a new security server E2E-SS3 with authentication certificate is registered with owner code e2e-tc2-member-subsystem
    And Management requests tab is selected
    And the option to show only pending requests is selected
    When the user clicks on search icon
    And the user enters e2e-tc2-member-subsystem in the search field
    Then the user views the Management request from Security server E2E-SS3 with owner code e2e-tc2-member-subsystem
    And the user should not see the Management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem

  Scenario: Search for Management Requests based on Free Text in Visible Columns
    Given Management requests tab is selected
    And the option to show only pending requests is selected
    When the user clicks the checkbox to show only pending requests
    Then the option to show only pending requests is not selected
    When the user clicks on search icon
    And the user enters e2e-tc2-member-subsystem in the search field
    And the user should not see the Management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    Then the user views the Management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem
    When the user clicks Revoked request Add Certificate from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem
    And the details page is shown with title Add Certificate
    And user clicks back
    Then the option to show only pending requests is not selected
    And search field contains 'e2e-tc2-member-subsystem'
    And the user views the Management request from Security server E2E-SS2 with owner code e2e-tc2-member-subsystem
    And the user clears the search field
    And the user clicks the checkbox to show only pending requests

  Scenario: User Approves Management Request for additional authentication certificate
    Given new authentication certificate for a security server E2E-SS1 is registered with owner code e2e-tc1-member-subsystem
    And Management requests tab is selected
    And the option to show only pending requests is selected
    When the user views the Management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    And the user clicks on the Approve button in the row from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    Then the pending management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem should be removed from the list

  Scenario: User Approves Management Request for adding another security server
    Given a new security server E2E-SS3 with authentication certificate is registered with owner code e2e-tc1-member-subsystem
    And Management requests tab is selected
    And the option to show only pending requests is selected
    When the user views the Management request from Security server E2E-SS3 with owner code e2e-tc1-member-subsystem
    And the user clicks on the Approve button in the row from Security server E2E-SS3 with owner code e2e-tc1-member-subsystem
    Then the pending management request from Security server E2E-SS3 with owner code e2e-tc1-member-subsystem should be removed from the list

  Scenario: User Approves Management Request for adding another client to security server
    Given a client with code e2e-tc3-member-subsystem and subsystem code e2e-tc3-subsystem is registered in security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    And Management requests tab is selected
    And the option to show only pending requests is selected
    When the user clicks Pending request Add Client from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem
    Then the details page is shown with title Add Client
    And the details page displays the Request Information for the PENDING management request
    And the details page displays the Affected Security Server Information for the Client request
    And the details page displays the client information for the Client Submitted for Registration
    When the user clicks Approve button
    Then the pending management request from Security server E2E-SS1 with owner code e2e-tc1-member-subsystem should be removed from the list
