@ManagementRequests
Feature: Management requests API

  Background:
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:E2E:member-1' is added
    And Authentication header is set to MANAGEMENT_SERVICE

  @Modifying
  Scenario: Add/delete Authentication certificate
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And user can get security server 'CS:E2E:member-1:SS-X' authentication certificates
    And Authentication header is set to MANAGEMENT_SERVICE
    When authentication certificate of 'CS:E2E:member-1:SS-X' is deleted
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:E2E:member-1:SS-X' has no authentication certificates

  @Modifying
  Scenario: Add/delete Authentication certificate for non existing member
    Given new security server 'CS:E2E:member-2:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And member 'CS:E2E:member-2' does not exist
    When management request is approved
    Then user can get security server 'CS:E2E:member-2:SS-X' authentication certificates
    And member 'CS:E2E:member-2' name is 'member-2'
    When Authentication header is set to MANAGEMENT_SERVICE
    And authentication certificate of 'CS:E2E:member-2:SS-X' is deleted
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:E2E:member-2:SS-X' has no authentication certificates

  @Modifying
  Scenario: Add/delete not yet approved Authentication certificate
    And new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is with status 'WAITING'
    When Authentication header is set to MANAGEMENT_SERVICE
    And authentication certificate of 'CS:E2E:member-1:SS-X' is deleted
    And Authentication header is set to REGISTRATION_OFFICER
    Then management request list endpoint queried and verified using params
      | $q | $status | $origin | $serverId            | $types                         | $sortBy | $desc | $pageSize | $page | $itemsInPage | $total | $sortFieldExp |
      |    | REVOKED |         | CS:E2E:member-1:SS-X | AUTH_CERT_REGISTRATION_REQUEST |         |       | 5         | 1     | 1            | 1      |               |
      |    |         |         | CS:E2E:member-1:SS-X | AUTH_CERT_DELETION_REQUEST     |         |       | 5         | 1     | 1            | 1      |               |

  @Modifying
  Scenario: Auto approve Authentication certificate
    Given Authentication header is set to REGISTRATION_OFFICER
    And new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'CENTER'
    And management request is with status 'WAITING'
    When Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    Then management request is with status 'APPROVED'
    And user can get security server 'CS:E2E:member-1:SS-X' authentication certificates


  @Modifying
  Scenario: Dont' auto approve Authentication certificate for non existing member
    Given Authentication header is set to REGISTRATION_OFFICER
    And member 'CS:E2E:member-2' does not exist
    When Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-2:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    Then management request is with status 'WAITING'
    And member 'CS:E2E:member-2' does not exist

  @Modifying
  Scenario: Decline authentication certificate registration
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is with status 'WAITING'
    When management request is declined
    Then management request is with status 'DECLINED'
    And member 'CS:E2E:member-1' is not in global group 'security-server-owners'

  @Modifying
  Scenario: Register member as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    Then security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2'

  @Modifying
  Scenario: Register non existing member as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    Then security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2'
    And member 'CS:E2E:member-2' name is 'member-2'

  @Modifying
  Scenario: Delete member as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2'
    And Authentication header is set to MANAGEMENT_SERVICE
    When 'CS:E2E:member-2' is deleted as security server 'CS:E2E:member-1:SS-X' client
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:E2E:member-1:SS-X' has no clients

  @Modifying
  Scenario: Delete still pending member as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is with status 'WAITING'
    And Authentication header is set to MANAGEMENT_SERVICE
    When 'CS:E2E:member-2' is deleted as security server 'CS:E2E:member-1:SS-X' client
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:E2E:member-1:SS-X' has no clients
    And management request list endpoint queried and verified using params
      | $q | $status | $origin | $serverId            | $types                      | $sortBy | $desc | $pageSize | $page | $itemsInPage | $total | $sortFieldExp |
      |    | REVOKED |         | CS:E2E:member-1:SS-X | CLIENT_REGISTRATION_REQUEST |         |       | 5         | 1     | 1            | 1      |               |
      |    |         |         | CS:E2E:member-1:SS-X | CLIENT_DELETION_REQUEST     |         |       | 5         | 1     | 1            | 1      |               |

  @Modifying
  Scenario: Auto approve registration of member as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And Authentication header is set to REGISTRATION_OFFICER
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'CENTER'
    And management request is with status 'WAITING'
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    Then management request is with status 'APPROVED'
    And security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2'

  @Modifying
  Scenario: Don't auto approve registration of non existing member as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    Then management request is with status 'WAITING'
    And member 'CS:E2E:member-2' does not exist

  @Modifying
  Scenario: Decline registration of member as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is with status 'WAITING'
    When management request is declined
    Then management request is with status 'DECLINED'

  @Modifying
  Scenario: Register a new subsystem as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And member 'CS:E2E:member-2' subsystems does not contain 'subsystem-1'
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2:subsystem-1' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    Then security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2:subsystem-1'
    And member 'CS:E2E:member-2' subsystems contains 'subsystem-1'

  @Modifying
  Scenario: Register a new subsystem of non existing member as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And member 'CS:E2E:member-2' does not exist
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2:subsystem-1' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    Then security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2:subsystem-1'
    And member 'CS:E2E:member-2' name is 'member-2'
    And member 'CS:E2E:member-2' subsystems contains 'subsystem-1'

  @Modifying
  Scenario: Register an existing subsystem as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And new subsystem 'CS:E2E:member-2:subsystem-1' is added
    And member 'CS:E2E:member-2' subsystems contains 'subsystem-1'
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2:subsystem-1' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    Then security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2:subsystem-1'
    And member 'CS:E2E:member-2' subsystems contains 'subsystem-1'

  @Modifying
  Scenario: Delete subsystem as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And member 'CS:E2E:member-2' subsystems does not contain 'subsystem-1'
    And Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2:subsystem-1' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And Authentication header is set to MANAGEMENT_SERVICE
    When 'CS:E2E:member-2:subsystem-1' is deleted as security server 'CS:E2E:member-1:SS-X' client
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:E2E:member-1:SS-X' has no clients
    And member 'CS:E2E:member-2' subsystems contains 'subsystem-1'

  @Modifying
  Scenario: Auto approve registration of subsystem as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And member 'CS:E2E:member-2' subsystems does not contain 'subsystem-1'
    And Authentication header is set to REGISTRATION_OFFICER
    And client 'CS:E2E:member-2:subsystem-1' is registered as security server 'CS:E2E:member-1:SS-X' client from 'CENTER'
    And management request is with status 'WAITING'
    And member 'CS:E2E:member-2' subsystems does not contain 'subsystem-1'
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2:subsystem-1' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    Then management request is with status 'APPROVED'
    And security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2'
    And member 'CS:E2E:member-2' subsystems contains 'subsystem-1'

  @Modifying
  Scenario: Dont't auto approve registration of subsystem of non existing member as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And member 'CS:E2E:member-2' does not exist
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2:subsystem-1' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is with status 'WAITING'
    And member 'CS:E2E:member-2' does not exist

  @Modifying
  Scenario: Decline registration of subsystem as security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And member 'CS:E2E:member-2' subsystems does not contain 'subsystem-1'
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2:subsystem-1' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is with status 'WAITING'
    And management request is declined
    Then management request is with status 'DECLINED'
    And member 'CS:E2E:member-2' subsystems does not contain 'subsystem-1'

  @Modifying
  Scenario: Changing security server owner
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And member 'CS:E2E:member-1' is in global group 'security-server-owners'
    And new member 'CS:E2E:member-2' is added
    And member 'CS:E2E:member-2' is not in global group 'security-server-owners'
    And Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And security server 'CS:E2E:member-1:SS-X' clients do not contain 'CS:E2E:member-1'
    And security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2'
    And Authentication header is set to MANAGEMENT_SERVICE
    When owner of security server 'CS:E2E:member-1:SS-X' is changed to 'CS:E2E:member-2'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    Then management request is with status 'APPROVED'
    And security server 'CS:E2E:member-2:SS-X' clients contains 'CS:E2E:member-1'
    And security server 'CS:E2E:member-2:SS-X' clients do not contain 'CS:E2E:member-2'
    And member 'CS:E2E:member-1' is not in global group 'security-server-owners'
    And member 'CS:E2E:member-2' is in global group 'security-server-owners'
    And member 'CS:E2E:member-2' owned servers contains 'CS:E2E:member-2:SS-X'

  @Modifying
  Scenario: View management request details
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    Then management request is with status 'WAITING'
    And details of management request can be retrieved for security server 'CS:E2E:member-1:SS-X'

  @Modifying
  Scenario: Disabling/enabling subsystem
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-1:subsystem-1' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    When Authentication header is set to MANAGEMENT_SERVICE
    And security server 'CS:E2E:member-1:SS-X' client 'CS:E2E:member-1:subsystem-1' is disabled
    And Authentication header is set to REGISTRATION_OFFICER
    Then member 'CS:E2E:member-1' subsystem 'subsystem-1' status in server 'SS-X' is 'DISABLED'
    When Authentication header is set to MANAGEMENT_SERVICE
    And security server 'CS:E2E:member-1:SS-X' client 'CS:E2E:member-1:subsystem-1' is enabled
    And Authentication header is set to REGISTRATION_OFFICER
    Then member 'CS:E2E:member-1' subsystem 'subsystem-1' status in server 'SS-X' is 'APPROVED'

  @Modifying
  Scenario: Management requests list
    Given Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:E2E:member-2' is added
    And new member 'CS:E2E:member-3' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-1:SS-1' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-1:SS-2' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-1:SS-3' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-1:SS-4' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is declined
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-1:SS-5' authentication certificate registered with origin 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-1' client from 'CENTER'
    And management request is approved
    And Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-3' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And Authentication header is set to MANAGEMENT_SERVICE
    And owner of security server 'CS:E2E:member-1:SS-3' is changed to 'CS:E2E:member-2'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And Authentication header is set to MANAGEMENT_SERVICE
    And authentication certificate of 'CS:E2E:member-1:SS-2' is deleted
    And client 'CS:E2E:member-3' is registered as security server 'CS:E2E:member-1:SS-1' client from 'SECURITY_SERVER'
    And Authentication header is set to REGISTRATION_OFFICER
    And management request is approved
    And Authentication header is set to MANAGEMENT_SERVICE
    And 'CS:E2E:member-3' is deleted as security server 'CS:E2E:member-1:SS-1' client
    And Authentication header is set to REGISTRATION_OFFICER
    Then management request list endpoint queried and verified using params
      | $q   | $status  | $origin         | $serverId            | $types                                                 | $sortBy               | $desc | $pageSize | $page | $itemsInPage | $total | $sortFieldExp                 |
      |      |          |                 |                      |                                                        |                       |       |           |       | 11           | 11     |                               |
      |      |          |                 |                      |                                                        |                       |       | 5         | 1     | 5            | 11     |                               |
      |      |          |                 |                      |                                                        |                       |       | 5         | 2     | 5            | 11     |                               |
      |      |          |                 |                      |                                                        |                       |       | 3         | 9     | 0            | 11     |                               |
      | SS   |          |                 |                      |                                                        |                       |       | 5         | 9     | 0            | 11     |                               |
      | SS-3 |          |                 |                      |                                                        |                       |       | 5         | 9     | 0            | 3      |                               |
      | SS-5 | WAITING  |                 |                      |                                                        |                       |       | 5         | 1     | 1            | 1      |                               |
      |      | DECLINED |                 |                      |                                                        |                       |       | 5         | 1     | 1            | 1      |                               |
      |      | APPROVED |                 |                      |                                                        |                       |       | 5         | 1     | 5            | 7      |                               |
      |      | APPROVED |                 |                      |                                                        |                       |       | 5         | 2     | 2            | 7      |                               |
      | SS-3 | APPROVED |                 |                      |                                                        |                       |       | 5         | 1     | 3            | 3      |                               |
      |      |          | CENTER          |                      |                                                        |                       |       |           | 1     | 1            | 1      |                               |
      |      |          | SECURITY_SERVER |                      |                                                        |                       |       | 3         | 1     | 3            | 10     |                               |
      | SS-3 | APPROVED | SECURITY_SERVER |                      |                                                        |                       |       | 4         | 1     | 3            | 3      |                               |
      |      |          |                 | CS:E2E:member-1:SS-4 |                                                        |                       |       |           |       | 1            | 1      |                               |
      |      |          |                 |                      | AUTH_CERT_REGISTRATION_REQUEST                         |                       |       |           |       | 5            | 5      |                               |
      |      |          |                 |                      | CLIENT_REGISTRATION_REQUEST                            |                       |       | 2         | 1     | 2            | 3      |                               |
      |      |          |                 |                      | OWNER_CHANGE_REQUEST                                   |                       |       |           |       | 1            | 1      |                               |
      |      |          |                 |                      | CLIENT_DELETION_REQUEST                                |                       |       |           |       | 1            | 1      |                               |
      |      |          |                 |                      | AUTH_CERT_DELETION_REQUEST                             |                       |       |           |       | 1            | 1      |                               |
      |      | APPROVED |                 |                      | AUTH_CERT_REGISTRATION_REQUEST,CLIENT_DELETION_REQUEST |                       |       |           |       | 3            | 3      |                               |
      | SS-1 |          |                 |                      | AUTH_CERT_REGISTRATION_REQUEST,CLIENT_DELETION_REQUEST |                       |       |           |       | 2            | 2      |                               |
      |      |          | CENTER          |                      | OWNER_CHANGE_REQUEST                                   |                       |       |           |       | 0            | 0      |                               |
      |      |          |                 |                      |                                                        | id                    | true  |           |       | 11           | 11     | id.toString()                 |
      |      |          |                 |                      |                                                        | created_at            | false |           |       | 11           | 11     | created_at                    |
      |      |          |                 |                      |                                                        | type                  | true  |           |       | 11           | 11     | type                          |
      |      |          |                 |                      |                                                        | security_server_owner | false |           |       | 11           | 11     | security_server_owner         |
      |      |          |                 |                      |                                                        | security_server_id    | true  |           |       | 11           | 11     | security_server_id.encoded_id |
      |      |          |                 |                      |                                                        | status                | false |           |       | 11           | 11     | status                        |
