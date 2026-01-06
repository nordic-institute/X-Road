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

  Scenario: Request is limited to 100000 bytes
    Given more than 100000 bytes request is sent
    Then Response of status code 500 and soap faultCode "bad_request" and soap faultString "Request size limit 100000 exceeded" is returned
