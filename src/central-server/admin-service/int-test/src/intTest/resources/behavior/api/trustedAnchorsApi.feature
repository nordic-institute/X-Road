@TrustedAnchorsApi
Feature: Trusted Anchors Api

  Scenario: User uploads trusted anchor file for preview
    Given Authentication header is set to SECURITY_OFFICER
    When user uploads trusted anchor 'trusted-anchor.xml' for preview
    Then Response is of status code 200
    And trusted anchor response contains instance 'CS0' and hash '40:2A:4F:94:05:D2:9B:ED:C9:EE:A2:6D:EC:EC:11:94:5D:C9:A8:3E:29:1F:B2:92:A6:E4:DF:1D'
    When user uploads trusted anchor 'trusted-anchor-invalid-1.xml' for preview
    Then Response is of status code 400 and error code 'malformed_anchor'
    When user uploads trusted anchor 'trusted-anchor-invalid-2.xml' for preview
    Then Response is of status code 400 and error code 'malformed_anchor'
    When trusted anchor file "files/attack/script.sh" as 'script.sh' is uploaded
    Then Response is of status code 400 and error code 'invalid_file_extension'
    When trusted anchor file "files/attack/script.sh" as 'script.sh.xml' is uploaded
    Then Response is of status code 400 and error code 'double_file_extension'
    When trusted anchor file "files/attack/script.sh" as 'trusted-anchor.xml' is uploaded
    Then Response is of status code 400 and error code 'invalid_file_content_type'
    When trusted anchors list is retrieved
    Then trusted anchors list contains 0 items

  @Modifying
  Scenario: Uploading trusted anchor file
    Given Authentication header is set to SECURITY_OFFICER
    When trusted anchor file 'trusted-anchor.xml' is uploaded
    And Response is of status code 201
    And trusted anchor response contains instance 'CS0' and hash '40:2A:4F:94:05:D2:9B:ED:C9:EE:A2:6D:EC:EC:11:94:5D:C9:A8:3E:29:1F:B2:92:A6:E4:DF:1D'
    Then uploaded trusted anchor is downloaded
    And download anchor matches trusted anchor file 'trusted-anchor.xml'

  @Modifying
  Scenario: Get trusted anchors list
    Given Authentication header is set to SECURITY_OFFICER
    And trusted anchor file 'trusted-anchor.xml' is uploaded
    And trusted anchor file 'trusted-anchor-2.xml' is uploaded
    And trusted anchor file 'trusted-anchor-2.xml' is uploaded
    When trusted anchors list is retrieved
    Then Response is of status code 200
    And trusted anchors list contains 2 items
    And trusted anchors list contains hash '40:2A:4F:94:05:D2:9B:ED:C9:EE:A2:6D:EC:EC:11:94:5D:C9:A8:3E:29:1F:B2:92:A6:E4:DF:1D'
    And trusted anchors list contains hash '95:6C:C8:A5:9B:B5:51:5A:FB:9F:9C:84:38:C0:62:6B:93:48:AE:D7:54:44:16:0C:83:28:59:54'
    When trusted anchor is deleted by hash '40:2A:4F:94:05:D2:9B:ED:C9:EE:A2:6D:EC:EC:11:94:5D:C9:A8:3E:29:1F:B2:92:A6:E4:DF:1D'
    And Response is of status code 204
    And trusted anchors list is retrieved
    Then trusted anchors list contains 1 items
    And trusted anchors list contains hash '95:6C:C8:A5:9B:B5:51:5A:FB:9F:9C:84:38:C0:62:6B:93:48:AE:D7:54:44:16:0C:83:28:59:54'

  Scenario: Deleting non existing trusted anchor
    Given Authentication header is set to SECURITY_OFFICER
    When trusted anchor is deleted by hash 'non:existing'
    Then Response is of status code 404 and error code 'trusted_anchor_not_found'

  Scenario: Upload is forbidden for non privileged user role SYSTEM_ADMINISTRATOR
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    When user uploads trusted anchor 'trusted-anchor.xml' for preview
    Then Response is of status code 403
    When trusted anchor file 'trusted-anchor.xml' is uploaded
    Then Response is of status code 403
    When trusted anchor is deleted by hash 'any'
    Then Response is of status code 403

  Scenario: Upload is forbidden for non privileged user role REGISTRATION_OFFICER
    Given Authentication header is set to REGISTRATION_OFFICER
    When user uploads trusted anchor 'trusted-anchor.xml' for preview
    Then Response is of status code 403
    When trusted anchor file 'trusted-anchor.xml' is uploaded
    Then Response is of status code 403
    When trusted anchors list is retrieved
    Then Response is of status code 403
    When trusted anchor is deleted by hash 'any'
    Then Response is of status code 403

  Scenario: Download is forbidden for non privileged user role REGISTRATION_OFFICER
    Given Authentication header is set to REGISTRATION_OFFICER
    When trusted anchor with hash: '95:6C:C8:A5:9B:B5:51:5A:FB:9F:9C:84:38:C0:62:6B:93:48:AE:D7:54:44:16:0C:83:28:59:54' is downloaded
    Then Response is of status code 403
