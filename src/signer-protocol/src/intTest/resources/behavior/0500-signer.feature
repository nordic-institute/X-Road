Feature: 0500 - Signer

  Scenario: Initialization
    Given tokens list contains token "0"
    And token "0" status is "NOT_INITIALIZED"
    When signer is initialized with pin "1234"
    Then token "0" is not active
    And token "0" status is "OK"

  Scenario: Activate token
    Given token "0" is not active
    When token "0" is logged in with pin "1234"
    Then token "0" is active

  Scenario: Deactivate token
    When token "0" is logged out
    Then token "0" is not active

  Scenario: Update token pin
    Given token "0" is not active
    And token "0" is logged in with pin "1234"
    When token "0" pin is updated from "1234" to "4321"
    And token "0" is logged in with pin "4321"
    Then token "0" is active

  Scenario: Set token friendly name
    When name "New friendly name" is set for token "0"
    Then token "0" name is "New friendly name"

  Scenario: Key generation
    When new key "key-1" generated for token "0"
    And name "First key" is set for generated key
    When new key "key-2" generated for token "0"
    And name "Second key" is set for generated key
    When new key "key-3" generated for token "0"
    And name "Third key" is set for generated key
    Then token "0" has exact keys "First key,Second key,Third key"
    And sign mechanism for token "0" key "Second key" is not null

  Scenario: Delete key
    Given new key "key-X" generated for token "0"
    And name "KeyX" is set for generated key
    Then token info can be retrieved by key id
    When key "Third key" is deleted from token "0"
    Then token "0" has exact keys "First key,Second key,KeyX"

  Scenario: Sign
    Given digest can be signed using key "KeyX" from token "0"

  Scenario: Miscellaneous checks
    * check token "0" key "First key" batch signing enabled

#  not covered SignerProxy methods:
#  String importCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId) #partly in GenerateSelfSignedCert
#  AuthKeyInfo getAuthKey(SecurityServerId serverId)                            #requires valid ocsp response
#  void setOcspResponses(String[] certHashes, String[] base64EncodedResponses)  #requires valid ocsp responses
#  String[] getOcspResponses(String[] certHashes)                               #requires valid ocsp responses
#  MemberSigningInfoDto getMemberSigningInfo(ClientId clientId)                 #requires valid ocsp response
#  boolean isHSMOperational()                                                   #timeout. no ModuleManager actor
