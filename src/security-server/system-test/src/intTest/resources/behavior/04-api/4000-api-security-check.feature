Feature: 4000 - SS: API security check

  Scenario: Verify all endpoints fail when called without authorization
    * All endpoints should fail with status code 401 without authorization header
