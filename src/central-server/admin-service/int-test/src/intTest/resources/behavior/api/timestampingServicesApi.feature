@TimestampingServices
Feature: Timestamping services API

  Background:
    Given Authentication header is set to SYSTEM_ADMINISTRATOR

  @Modifying
  Scenario: View the list of timestamping services
    When timestamping service is added
    Then timestamping services list contains added timestamping service

  @Modifying
  Scenario: View timestamping service by id
    When timestamping service is added
    Then timestamping services returns added timestamping service by id

  Scenario: Adding the timestamping service with invalid url is not allowed
    When user tries to add timestamping service with invalid url
    Then creating timestamping service fails with exception

  @Modifying
  Scenario: Deleting timestamping service
    Given timestamping service is added
    And timestamping services list contains added timestamping service
    When user deletes the added timestamping service
    Then timestamping services list does not contain added timestamping service

  Scenario: Deleting not existing timestamping service
    When user tries to delete timestamping service with not existing id
    Then timestamping service is not found

  @Modifying
  Scenario: Modifying the timestamp service URL
    Given timestamping service is added
    And timestamping services list contains added timestamping service
    Then timestamping service URL is updated

  @Modifying
  Scenario: Modifying the timestamp service URL and certificate
    Given timestamping service is added
    And timestamping services list contains added timestamping service
    Then timestamping service URL and certificate are updated
