@CentralServer
@ManagementRequests
@LoadingTesting
Feature: CS: Management Requests

  Background:
    Given CentralServer login page is open
    Then Browser is set in CELLULAR2G network speed
    And User xrd logs in to CentralServer with password secret
    And Management requests tab is selected

  @Modifying
  Scenario: Management Requests list is correctly shown
    When Add Management request
    And Management Requests table with columns Id, Created is visible
    Then User is able to sort the table by column 1
    And User is able to sort the table by column 2
    And User is able to sort the table by column 3

  @Modifying
  Scenario: That only pending requests are shown by default and it can be controlled by the checkmark
    When Add Management request
    And Management Requests table is visible
    And Show only pending requests is not checked
    #Then The user can not see the approve / decline actions for requests that have already been processed
    When Show only pending requests is checked
    #Then That the user can see the approve / decline actions for pending management requests

  Scenario: The user can approve a request
    When Add Management request
    And Management Requests table is visible
    Then Show only pending requests is not checked
   # And User can approve a request
   # Then The table is updated immediately after a request is approved

  Scenario: The user can declined a request
    When Add Management request
    And Management Requests table is visible
    Then Show only pending requests is not checked
    #And User can declined a request
    #Then The table is updated immediately after a request is declined


