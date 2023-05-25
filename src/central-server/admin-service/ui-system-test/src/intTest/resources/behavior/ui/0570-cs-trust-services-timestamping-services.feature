@CentralServer
@CertificationService
@LoadingTesting
Feature: 0570 - CS: Trust Services: Timestamping Services

  Background:
    Given CentralServer login page is open
    Then Browser is set in CELLULAR2G network speed
    And User xrd logs in to CentralServer with password secret
    And TrustServices tab is selected

  Scenario: Add timestamping service
    When Timestamping service with URL http://e2e-test-timestamping-service1.com is added
    Then Timestamping service with URL http://e2e-test-timestamping-service1.com is visible in the Timestamping Services list

  Scenario: Timestamping services list is correctly shown
    When Timestamping service table with columns Url, Timestamping interval, Cost is visible
    And Timestamping service with URL http://e2e-test-timestamping-service2.com is added
    Then user is able to sort the table by column 1
    And user is able to sort the table by column 2
    And user is able to sort the table by column 3
    And User is able to view the certificate of Timestamping service with URL http://e2e-test-timestamping-service2.com

  Scenario: Timestamping service can be edit in list
    When Timestamping service with URL http://e2e-test-timestamping-service3.com is added
    And User is able click Edit button in Timestamping service with URL http://e2e-test-timestamping-service3.com
    Then User is able view the certificate of Timestamping service
    When User is able click Edit button in Timestamping service with URL http://e2e-test-timestamping-service3.com
    Then User is able change the certificate of Timestamping service with URL http://e2e-test-timestamping-service3.com
    When User is able click Edit button in Timestamping service with URL http://e2e-test-timestamping-service3.com
    And User is able change the URL of Timestamping service to new URL http://new-e2e-test-timestamping-service3.com
    Then Timestamping service with URL http://new-e2e-test-timestamping-service3.com is visible in the Timestamping Services list

  Scenario: Timestamping service can be delete in list
    When Timestamping service with URL http://e2e-test-timestamping-service4.com is added
    Then User is able to click delete button in Timestamping service with URL http://e2e-test-timestamping-service4.com
    And Timestamping service with URL http://e2e-test-timestamping-service4.com should removed in list
