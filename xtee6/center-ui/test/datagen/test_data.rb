#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

def create_all_test_data
  create_system_parameters()
  create_members_and_security_servers()
  create_requests()
  create_approved_cas()
  create_tsps()
  create_global_groups()
  create_configuration_sources()
  create_configuration_anchors()
  create_identifier_mapping()
end

private

def create_system_parameters
  SystemParameter.create(
      :key => SystemParameter::INSTANCE_IDENTIFIER,
      :value => "EE")

  SystemParameter.create(
      :key => SystemParameter::CONF_SIGN_ALGO_ID,
      :value => "SHA512withRSA")

  SystemParameter.create(
      :key => SystemParameter::CONF_HASH_ALGO_URI,
      :value => "http://www.w3.org/2001/04/xmlenc#sha512")

  SystemParameter.create(
      :key => SystemParameter::CONF_SIGN_CERT_HASH_ALGO_URI,
      :value => "http://www.w3.org/2001/04/xmlenc#sha512")

  central_server_address = "127.0.0.1"

  SystemParameter.create(
      :key => SystemParameter::CENTRAL_SERVER_ADDRESS,
      :value => central_server_address)

  SystemParameter.create(
      :key => SystemParameter::AUTH_CERT_REG_URL,
      :value => "https://#{central_server_address}:3000/center-service/")

  SystemParameter.create(
      :key => SystemParameter::MANAGEMENT_SERVICE_PROVIDER_CLASS ,
      :value => "GOV")

  SystemParameter.create(
      :key => SystemParameter::MANAGEMENT_SERVICE_PROVIDER_CODE ,
      :value => "xroadkeskus")

  SystemParameter.create(
      :key => SystemParameter::SECURITY_SERVER_OWNERS_GROUP,
      :value => "security-server-owners")
end

def create_members_and_security_servers
  riigiasutus = create_member_classes[0]
  first_member_code = "12345678"

  first_member = XroadMember.create!(
      :member_code => first_member_code,
      :name => "Turvaserveritega liige",
      :member_class => riigiasutus,
      :administrative_contact => "vladislav@turvaserver.com")

  first_subsystem = Subsystem.create!(
      :xroad_member_id => first_member.id,
      :subsystem_code => "firstSubsystemCode")

  second_subsystem = Subsystem.create!(
      :xroad_member_id => first_member.id,
      :subsystem_code => "secondSubsystemCode")

  second_member = XroadMember.create!(
      :member_code => "87654321",
      :name => "Yhe turvaserveriga liige",
      :member_class => riigiasutus,
      :administrative_contact => "vitja@turvaserver.com")

  owned1, used1, used2 = create_security_servers(first_member, second_member)

  SecurityCategory.create!(
      :code => "K0",
      :description => "Low Security",
      :security_servers => [owned1, used1])

  SecurityCategory.create!(
      :code => "K1",
      :description => "Medium Security",
      :security_servers => [owned1])

  SecurityCategory.create!(
      :code => "K2",
      :description => "Above medium security",
      :security_servers => [owned1, used1, used2])

  SecurityCategory.create!(
      :code => "K3",
      :description => "Extra strong security",
      :security_servers => [used1, used2])

  first_member.update_attributes!(:security_servers => [owned1, used1])
  first_subsystem.update_attributes!(:security_servers => [used1, used2])
end

def create_requests
  client_id = ClientId.from_parts("EE", "GOV", "12345678")
  server_id = SecurityServerId.from_parts("EE", "GOV",
      "12345678", "owned1")

  first_auth_cert_reg_request = AuthCertRegRequest.new(
      :security_server => SecurityServerId.from_parts("EE", "GOV",
          "12345678", "owned1"),
      :auth_cert => get_third_auth_cert,
      :address => "www.authcertreg.com",
      :origin => Request::SECURITY_SERVER)

  first_auth_cert_reg_request.register()

  second_auth_cert_reg_request = AuthCertRegRequest.new(
      :security_server => SecurityServerId.from_parts("EE", "GOV",
          "12345678", "owned1"),
      :auth_cert => get_third_auth_cert,
      :address => "www.authcertreg.com",
      :origin => Request::CENTER)

  second_auth_cert_reg_request.register()

  # This one is supposed to cancel third_auth_cert_reg_request
  auth_cert_deletion_request = AuthCertDeletionRequest.new(
      :security_server =>
        SecurityServerId.from_parts("EE", "GOV","87654321", "used1"),
      :auth_cert => get_second_auth_cert,
      :comments => "Registered experimentally",
      :origin => Request::CENTER)

  auth_cert_deletion_request.register()
end

def create_approved_cas
  # Actual data (especially certs) taken from:
  # systemtest/conf/testservers/clientmember/globalconf_NEW.xml
  first_approved_ca_certs = get_first_approved_ca_certs

  approved_ca = ApprovedCa.new
  approved_ca.authentication_only = false
  approved_ca.identifier_decoder_member_class = "GOV"
  approved_ca.identifier_decoder_method_name = "ee.ria.xroad.Extractor.extract"

  top_ca_ocsp = OcspInfo.new()
  top_ca_ocsp.url = "http://iks2-ubuntu.cyber.ee:8080/ejbca/publicweb/status/ocsp"
  top_ca_ocsp.cert = first_approved_ca_certs[:top_ca_ocsp_cert]

  top_ca = CaInfo.new()
  top_ca.cert = first_approved_ca_certs[:top_ca_cert]
  top_ca.ocsp_infos = [top_ca_ocsp]
  approved_ca.top_ca = top_ca

  approved_ca.save!
end

def create_tsps
  first_tsp = ApprovedTsa.new()
  first_tsp.url = "http://www.url2.com"
  first_tsp.cert = get_first_auth_cert()
  first_tsp.save!

  second_tsp = ApprovedTsa.new()
  second_tsp.url = "http://www.url1.com"
  second_tsp.cert = get_second_auth_cert()
  second_tsp.save!
end

def create_global_groups
  # Create global group for "riigiasutused"
  client_id = ClientId.from_parts("EE", "GOV", "12345678")
  group = GlobalGroup.create!(
      :group_code => "riigiasutused",
      :description => "Paljud riigiasutused kuuluvad siia")

  # Create global group for owners of security servers.
  group = GlobalGroup.create!(
      :group_code => "security-server-owners",
      :description => "Security server owners")

  # Add all the security server owners to the group.
  for server in SecurityServer.all
    owner = server.owner
    client_id = ClientId.from_parts(
        "EE",
        owner.member_class.code,
        owner.member_code)
    group.add_member(client_id)
  end

  # Save the system parameter for security-server-owners group.
  if SystemParameter.security_server_owners_group == nil
    SystemParameter.create!(
        :key => SystemParameter::SECURITY_SERVER_OWNERS_GROUP,
        :value => "security-server-owners")
  end
end

def create_identifier_mapping
  DistributedFiles.create!(
    :file_name => "identifiermapping.xml",
    :file_data => "<identifierMapping>",
    :content_identifier => "IDENTIFIERMAPPING"
  )
end

# Data unit generation methods - end

def create_member_classes
  riigiasutus = MemberClass.create!(:code => "GOV",
      :description => "Riigiasutuse klassi kirjeldus")
  ettevote = MemberClass.create!(:code => "ettevote",
      :description => "Ettevotte klassi kirjeldus")
  eraisik = MemberClass.create!(:code => "eraisik",
      :description => "Eraisiku klassi kirjeldus")

  [riigiasutus, ettevote, eraisik]
end

def create_security_servers(first_member, second_member)

  owned1 = SecurityServer.create!(
      :server_code => "owned1",
      :address => "iks2-test2.cyber.ee",
      :owner_id => first_member.id)

  used1 = SecurityServer.create!(
      :server_code => "used1",
      :address => "iks2-test1.cyber.ee",
      :owner_id => second_member.id)

  used2 = SecurityServer.create!(
      :server_code => "used2",
      :address => "www.used2.com",
      :owner_id => second_member.id)

  AuthCert.create!(
      :certificate => get_first_auth_cert,
      :security_server_id => owned1.id)

  AuthCert.create!(
      :certificate => get_second_auth_cert,
      :security_server_id => used1.id)

  [owned1, used1, used2]
end

def create_configuration_sources
  internal_signing_key = ConfigurationSigningKey.new(
    :key_identifier => "keyIdentifierInternal",
    :certificate => get_first_verification_cert()
  )

  internal_source = ConfigurationSource.new(
    :source_type => "internal",
    :configuration_signing_keys => [internal_signing_key],
    :active_key => internal_signing_key
  )

  internal_source.save!

  external_signing_key = ConfigurationSigningKey.new(
    :key_identifier => "keyIdentifierExternal",
    :certificate => get_second_verification_cert()
  )

  external_source = ConfigurationSource.new(
    :source_type => "external",
    :configuration_signing_keys => [external_signing_key],
    :active_key => external_signing_key
  )

  external_source.save!
end

def create_configuration_anchors
  anchor_url_cert = AnchorUrlCert.new(
    :certificate => get_first_auth_cert()
  )

  anchor_url = AnchorUrl.new(
    :url => "http://anchorurl.example.com",
    :anchor_url_certs => [anchor_url_cert]
  )

  configuration_anchor = TrustedAnchor.new(
    :instance_identifier => "EE",
    :anchor_urls => [anchor_url]
  )

  configuration_anchor.save!
end

# Auth certs - start

def get_first_auth_cert
  cert_base64 =
  "MIIDsjCCApqgAwIBAgIIfLhFu8lEKxUwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UE
  AwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0Uw
  HhcNMTMwNjIwMTkxMTM2WhcNMTUwNjIwMTkxMTM2WjApMScwJQYDVQQDDB5NRU1C
  RVI6RUUvcmlpZ2lhc3V0dXMvMTIzNDU2NzgwggEiMA0GCSqGSIb3DQEBAQUAA4IB
  DwAwggEKAoIBAQCKN0u8gbOY0GA05TUeaPx37mYEG65RBQE5hE+r4wX3cimMUW6U
  vb+RJU2W7um1YZPc14AFXu7IFiC2ldqpwpD5c1uW0bVdV2Ez6bFbz8sfc9+J/8c+
  VOnTEK5Hk6sO2R7tsgLQS0UpPpNiwtB4KGMcLYfEXTG8y4ZmsTEerDjW7lhyfjPi
  g7R2lJuyTmFIKE/LsBk35hsyXvLL6eWs/tAHgSBuzkGkdvRo0T36AlHmVQpz1GDL
  Znej3RJOLfL+K2FBooG5hjE5pxdbzZcNaHYKIoQhLG1KVXPTlO2P7Zfo7rQuH9bh
  ebXbBqFFnAEQxxs1TARErtg1D4CdxQ4SDgWxAgMBAAGjgc8wgcwwTQYIKwYBBQUH
  AQEEQTA/MD0GCCsGAQUFBzABhjFodHRwOi8vbG9jYWxob3N0OjgwODAvZWpiY2Ev
  cHVibGljd2ViL3N0YXR1cy9vY3NwMB0GA1UdDgQWBBRt9WjMcxPJ/miJpAr4bqWL
  5HzchTAMBgNVHRMBAf8EAjAAMB8GA1UdIwQYMBaAFHctiS4Dtv340mU5MTUi0EYa
  6NIJMA4GA1UdDwEB/wQEAwIDuDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUH
  AwIwDQYJKoZIhvcNAQEFBQADggEBAGuwy7q9xAO514K03RdyMJxStyeBQeP+X0lR
  +B/0KB7lBKZB4yykid8VAqOL6FUQePRCgXG4JywqpdGEWxSMQvnIE+Tfo3it/VXj
  65piZW2e15stzAe44nISzMDtQqbcStdSxBp7VqRBJHDqcTms73JKrPIbtkt65UHt
  jUCugAITiheCD5P5MU0WtyO050UWOMf/w/dd7eZVzhbM94L4ZXXrBZbPtXV4OzrG
  YUuICK3iezhbl5CXaV80xRxBlvYaJceIDgy1E9nV3c4BjEk6WnpckNQSAX7NA1Xw
  e0w6lDf7Mr8E6Hy6VFlcwdrrraKiDqONnmnA5pvrYOehbCJpp8k="

  Base64.decode64(cert_base64)
end

def get_second_auth_cert
  cert_base64 =
  "MIIDsjCCApqgAwIBAgIIaGDWnMLOljowDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UE
  AwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0Uw
  HhcNMTMwNjIwMTkxNDQ5WhcNMTUwNjIwMTkxNDQ5WjApMScwJQYDVQQDDB5NRU1C
  RVI6RUUvcmlpZ2lhc3V0dXMvODc2NTQzMjEwggEiMA0GCSqGSIb3DQEBAQUAA4IB
  DwAwggEKAoIBAQDGBS7RhK0sQlyViljZL44Vlf7hAnSXx84fqswiwilbcJf7x/Fp
  ioKRjY5aowiPGvjo+RFgsL8t7pfNuZdjdwrzS/7GUNx75atkiIHQ3cTigAqJUWpj
  B8t3fXlFrqytIzCp0yK7MEHdjjBgqEux/v4g3S3ODZMVRUvkYpKQt4xcOWy5UtDQ
  Amu2cMqpWkKGRt6PQrAEG5+NvWK9Lvu4Mkr1An6gvsz4zDZ19JQYjoE871jycDJ9
  f6pnAoN3J9hIKWQ9LzydooeBeNEiA2n8VHpMNspnvsxLwGcGcss7O5FPd11FaPAt
  vxWQ71Zz+n1ChDFzWZ99JwLGPfW/nCeAFOntAgMBAAGjgc8wgcwwTQYIKwYBBQUH
  AQEEQTA/MD0GCCsGAQUFBzABhjFodHRwOi8vbG9jYWxob3N0OjgwODAvZWpiY2Ev
  cHVibGljd2ViL3N0YXR1cy9vY3NwMB0GA1UdDgQWBBRQKIwLygkuzIwqTPV/SKub
  aszqATAMBgNVHRMBAf8EAjAAMB8GA1UdIwQYMBaAFHctiS4Dtv340mU5MTUi0EYa
  6NIJMA4GA1UdDwEB/wQEAwIDuDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUH
  AwIwDQYJKoZIhvcNAQEFBQADggEBADVaSFjr9NIGYeCn1u3sNU/AkNX9LhQc0Aop
  f9RieV7MSpyUT50Issp8hoZ+4nQLTpfigT1/dJI6EMq/1qAm1f5BNe0ogy37LCbL
  +VKf+Eus7+0wFFH490uGFyzu/pqLkznriTkNIeQLOgPw/wYEyuTfVrcwgjI4+Wkp
  2uPZzBZ7S4G8SV4rS8BrL32JWNfTHZX660AaU4GlawL7HuVkbtpRqCeJ8cvZHxHT
  Xof/NbEoI13/fnDl33EjTf7zRtA6v+BNV+NxtuUUozLvWwYQPsku7E+Uh7dzGkDO
  934hndlmtm7+nZLqBoRd/7GwOShAzpXi3HqxD9SA2wOt8TZXmKg="

  Base64.decode64(cert_base64)
end

def get_third_auth_cert
  cert_base64 =
 "MIIDiDCCAnCgAwIBAgIIC/Tasr5oaKswDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UE
  AwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0Uw
  HhcNMTMwMjExMTIyMjQzWhcNMTUwMjExMTIyMjQzWjATMREwDwYDVQQDDAhjb25z
  dW1lcjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJnc3kMx4hbH6LPm
  peXkt3Lwj6EdRnmPGc0YjR0lZMC4qJVPocXtfmN+1MyUbhQONP2UgWWswqDZTKwr
  ycfx9DOlBaOfbUkt4GG8/D1HR3rdmIENC7XI5mYGlic7J/b1qQSLsGAzTPmRMRZ4
  jKMPb1lcrPnZxT+0VZcAy28eMZ4kf4YsdfsmgdZVqwtIt/TZv7d6Y9Ku0Ty0UeSQ
  0jgb5FxnCXRKL3edrOVfhXcYbXzPC7i91xHfqWm74hu63jGvlpN6zM598Tgc7ZhC
  nc83SF3571TO1q2Msqw4pCK8AVI6chJa+237lK+EU+nmlq+c+WZM+kveP75WrlNN
  tsxBny8CAwEAAaOBuzCBuDBYBggrBgEFBQcBAQRMMEowSAYIKwYBBQUHMAGGPGh0
  dHA6Ly9pa3MyLXVidW50dS5jeWJlci5lZTo4MDgwL2VqYmNhL3B1YmxpY3dlYi9z
  dGF0dXMvb2NzcDAdBgNVHQ4EFgQU+vRmVYcwfCkcSkxoRzxtyALpTt4wDAYDVR0T
  AQH/BAIwADAfBgNVHSMEGDAWgBR3LYkuA7b9+NJlOTE1ItBGGujSCTAOBgNVHQ8B
  Af8EBAMCBeAwDQYJKoZIhvcNAQEFBQADggEBABNtG8An2Kx/tXwvFRSelDCRwHsg
  cyX5enF73t+xGAOs8J8cEXZ24vFMP/Dn+uu9fUE/ZxypaWUDnUUGrnxbcGnPuyy3
  BrON88//Q9LdEZGJarNdRQXKQkC4FwXzLK+ha6PRqFgAj2ztCwt9QUIvrCnfomfh
  pCE6ivOUrcml3Sps2l3HtZcizfwVEysOEyNaZM6XtDcvPYeWoXuuJCsQc4qJWjoL
  vM1eC6qqqXNLoolVB5BWshuSNhwNyKMjxxsW08GCd9jJYMvasoDX+jQCUctEsZVT
  JRPVUNrOL3zRpWzZtt2+3yZ3dGp7uGri6TjsakMAS+QU65Ha+bfBXx87hwE="

  Base64.decode64(cert_base64)
end
# Auth certs - end

# Verification certs - start

def get_first_verification_cert
  return read_cert_file("test/resources/cert_sign_internal_AAA.pem")
end

def get_second_verification_cert
  return read_cert_file("test/resources/cert_sign_internal_BBB.pem")
end

# Verification certs - end

# Certs related to approved_cas - start

def get_first_approved_ca_certs
  top_ca_cert_base64 =
  "MIIDUzCCAjugAwIBAgIIU1eWoysptjUwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UEAwwIQWRtaW
  5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0UwHhcNMTIwNjE0MTAwNDI5W
  hcNMjIwNjEyMTAwNDI5WjA3MREwDwYDVQQDDAhBZG1pbkNBMTEVMBMGA1UECgwMRUpCQ0EgU2Ft
  cGxlMQswCQYDVQQGEwJTRTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAILYKYehC6h
  sbPQnqBPWoatI8I7qZGow3otWO9pW8lMvjgNiE8Cl8oFQS+C3CaqSvHU+iXUVkIlHuAr6k30G/m
  m6JqU0zA2o2apt5HJFzkg/0/LvLbSB1S5e0VTRDhMncgJakEUJvHFL0aKtq1RigP9C7Zt5BdDyR
  BuJiutvaFjSeJ2sCQrHDcrJ1uAtdidv3z3Zih9O8CnalNZFltFf7M8pm+O+HIbumiA19kShJwvp
  cdC9fVPuwsF1Qbeo4SeKuDPU1KHq7ZP9Heh7P6eywxghkYC4yewjBu8COi1FJMGHt9OdD+rNa/G
  TZ7ULPFiCH8wwCTn+YUtFe5pFLkvCG7sCAwEAAaNjMGEwHQYDVR0OBBYEFHctiS4Dtv340mU5MT
  Ui0EYa6NIJMA8GA1UdEwEB/wQFMAMBAf8wHwYDVR0jBBgwFoAUdy2JLgO2/fjSZTkxNSLQRhro0
  gkwDgYDVR0PAQH/BAQDAgGGMA0GCSqGSIb3DQEBBQUAA4IBAQAnEhhOwvk2Goy+wRwZgQLkmv9Q
  IzBwP+Bwd45DQOJyPjVTukNoc5w1y1in7yR9T8Cv5Ba1ctAqclztwjhAYAhWcG/CSZ4RvX8zysb
  nthW6qhZGGz5KJATZhFYrIaNGqC9i0FfTe3PF3HaowqndnBFtwjV65mszTaTNp94LKhLk1ARc/B
  idplsM1cBSoA9VWvTANI8alKuJxh1QG9TbEJul1BTzA/wItMVDtganTDrQxmkP1NW7d+MsIB5AQ
  HABaXWgjygcqoMlLjyH/0QOP13iyvMXQU4jtSlaTmGm9CaC/xRQ10YD7AHHvq1P2cvfbQaV3I5x
  UYF7aqwM93ZjmptI"

  top_ca_ocsp_cert_base64 =
  "MIIDUzCCAjugAwIBAgIIU1eWoysptjUwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UEAwwIQWRtaW
  5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0UwHhcNMTIwNjE0MTAwNDI5W
  hcNMjIwNjEyMTAwNDI5WjA3MREwDwYDVQQDDAhBZG1pbkNBMTEVMBMGA1UECgwMRUpCQ0EgU2Ft
  cGxlMQswCQYDVQQGEwJTRTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAILYKYehC6h
  sbPQnqBPWoatI8I7qZGow3otWO9pW8lMvjgNiE8Cl8oFQS+C3CaqSvHU+iXUVkIlHuAr6k30G/m
  m6JqU0zA2o2apt5HJFzkg/0/LvLbSB1S5e0VTRDhMncgJakEUJvHFL0aKtq1RigP9C7Zt5BdDyR
  BuJiutvaFjSeJ2sCQrHDcrJ1uAtdidv3z3Zih9O8CnalNZFltFf7M8pm+O+HIbumiA19kShJwvp
  cdC9fVPuwsF1Qbeo4SeKuDPU1KHq7ZP9Heh7P6eywxghkYC4yewjBu8COi1FJMGHt9OdD+rNa/G
  TZ7ULPFiCH8wwCTn+YUtFe5pFLkvCG7sCAwEAAaNjMGEwHQYDVR0OBBYEFHctiS4Dtv340mU5MT
  Ui0EYa6NIJMA8GA1UdEwEB/wQFMAMBAf8wHwYDVR0jBBgwFoAUdy2JLgO2/fjSZTkxNSLQRhro0
  gkwDgYDVR0PAQH/BAQDAgGGMA0GCSqGSIb3DQEBBQUAA4IBAQAnEhhOwvk2Goy+wRwZgQLkmv9Q
  IzBwP+Bwd45DQOJyPjVTukNoc5w1y1in7yR9T8Cv5Ba1ctAqclztwjhAYAhWcG/CSZ4RvX8zysb
  nthW6qhZGGz5KJATZhFYrIaNGqC9i0FfTe3PF3HaowqndnBFtwjV65mszTaTNp94LKhLk1ARc/B
  idplsM1cBSoA9VWvTANI8alKuJxh1QG9TbEJul1BTzA/wItMVDtganTDrQxmkP1NW7d+MsIB5AQ
  HABaXWgjygcqoMlLjyH/0QOP13iyvMXQU4jtSlaTmGm9CaC/xRQ10YD7AHHvq1P2cvfbQaV3I5x
  UYF7aqwM93ZjmptI"

  {:top_ca_cert => Base64.decode64(top_ca_cert_base64),
      :top_ca_ocsp_cert => Base64.decode64(top_ca_ocsp_cert_base64)}
end

# Certs related to approved_cas - end

def get_tsp_cert
  tsp_cert_base64 =
  "MIICwjCCAaqgAwIBAgIIb+RPNmkfCdYwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UEAwwIQWRtaW
  5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0UwHhcNMTIxMTI5MTE1MzA2W
  hcNMTQxMTI5MTE1MzA2WjAVMRMwEQYDVQQDDAp0aW1lc3RhbXAxMIGfMA0GCSqGSIb3DQEBAQUA
  A4GNADCBiQKBgQCb55NVDtHzs91sflX3fatZWUS69rxkxDMpcGo6doJ7YaKrCMr3BZ3ZlDTfCdE
  osWocTcYXdm3CO8BXlZvhkvKyHN/hr0UzD0T8j8mBYoq3fGjTVTJOIG2yTsyT/3z3dpcMyGMWws
  iqOd9TTtI8DcR2cOvQzlLiV9hz/kB9iLJeSQIDAQABo3gwdjAdBgNVHQ4EFgQUbdmtvKHCe0+vh
  KP+ZcVUjmf5w/AwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBR3LYkuA7b9+NJlOTE1ItBGGujS
  CTAOBgNVHQ8BAf8EBAMCBkAwFgYDVR0lAQH/BAwwCgYIKwYBBQUHAwgwDQYJKoZIhvcNAQEFBQA
  DggEBAFJ3AJ4I4RTeMBWhN8RLPQdJzcd0VRp9FUyYhnIkR679nXU+ZbIyaQNx3+hPIbhcOMKxlK
  Gm0LcDnjHL4EuJ6Gb027vF7mSwFbcKPM+L23x2QLvuVcUEjcbP3Kcm93XCSu3RI71JINM+WinjX
  ke/COuFzhMWJcLYj7S5dGR53ya0NnSf7dlua5FLBRiOFA5kRWTft6RcEW0jGZzscL6wZn+hH99I
  ihjqgdxV1GydL+BgDMfryZzhl+h1WtTwv0Bi5Gs81v8UlNUTnCCfLu9fatHx85/ttFcXEyt9SQz
  e3NGcaR1i3kyZvNijzG3C+jrUnJ/lFs5AcIiPG0Emz6oZEYs="

  Base64.decode64(tsp_cert_base64)
end


def self.read_cert_file(filename)
  result = []

  IO.foreach(filename) do |each|
    result << each.strip()
  end

  return result.join("")
end
