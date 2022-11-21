@TimestampingServices
Feature: Timestamping services API

  @Modifying
  Scenario: View the list of timestamping services
    When timestamping service is added
    Then timestamping services list contains added timestamping service

  Scenario: Adding the timestamping service with invalid url is not allowed
    When user tries to add timestamping service with invalid url
    Then creating timestamping service fails with exception
