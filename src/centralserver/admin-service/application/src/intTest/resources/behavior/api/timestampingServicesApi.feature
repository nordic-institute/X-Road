@TimestampingServices
Feature: Timestamping services API

  @Modifying
  Scenario: View the list of timestamping services
    When timestamping service is added
    Then timestamping services list contains added timestamping service
