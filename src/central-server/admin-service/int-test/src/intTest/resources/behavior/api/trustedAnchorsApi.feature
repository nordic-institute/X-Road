@TrustedAnchorsApi
@RunThis
Feature: Trusted Anchors Api

  Scenario: User uploads trusted anchor file for preview
    Given Authentication header is set to SECURITY_OFFICER
    Then user can upload trusted anchor 'trusted-anchor.xml' for preview
    And uploading invalid trusted anchor file 'trusted-anchor-invalid-1.xml' fails with status code 400 and message 'malformed_anchor'
    And uploading invalid trusted anchor file 'trusted-anchor-invalid-2.xml' fails with status code 400 and message 'malformed_anchor'

  Scenario: Uploading trusted anchor file
    Given Authentication header is set to SECURITY_OFFICER
    When user uploads trusted anchor file 'trusted-anchor.xml'
#   todo: Then user can download trusted anchor file
