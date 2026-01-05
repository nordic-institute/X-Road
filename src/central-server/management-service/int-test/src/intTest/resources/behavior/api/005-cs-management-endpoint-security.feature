@ManagementRequests
Feature: Management requests API: Endpoint security

  Scenario Outline: verify http method <method> not allowed
    Given "<method>" request is sent to url "<url>"
    Then Response with status code 403 is returned
    And Response headers contains no other headers except "Date, Content-Length"
    Examples:
      | method  | url                       |
      | GET     | /managementservice/manage |
      | PUT     | /managementservice/manage |
      | DELETE  | /managementservice/manage |
      | PATCH   | /managementservice/manage |
      | HEAD    | /managementservice/manage |
      | OPTIONS | /managementservice/manage |

  Scenario Outline: verify not existing endpoint (<method> <url>) returns 403
    Given "<method>" request is sent to url "<url>"
    Then Response with status code 403 is returned
    And Response headers contains no other headers except "Date, Content-Length"
    Examples:
      | method  | url                         |
      | GET     | /managementservice/manage-x |
      | POST    | /managementservice/manage-x |
      | HEAD    | /managementservice/health   |
      | OPTIONS | /managementservice/info     |
      | GET     | /something                  |
      | POST    | /something                  |
