require "global_conf_generator"

def get_cert_bytes
  Base64.decode64(
  "MIIDiDCCAnCgAwIBAgIIW99Q5VUloqswDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UEAwwIQWRtaW
  5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0UwHhcNMTIwOTI4MTgxNzM5W
  hcNMTQwOTI4MTgxNzM5WjATMREwDwYDVQQDDAhjb25zdW1lcjCCASIwDQYJKoZIhvcNAQEBBQAD
  ggEPADCCAQoCggEBAILY5AcoHHeoHIYqrrjaadQJwJlwMFN8mT/txE4/oKUWecvikwk1RNJNH0s
  +D9iUoCsCYqlU7PXbIXIelkH08ehgsdi5OmNAiG0fxEIouPDDOg5L5c4wxOm1/vVf0H+yBrv1OW
  UfEnCwsiRmqRN1JU9LH1GkVulPdqCMbicqlbidTTfYcFwf4R7RfOFeHrrNJSBvRev+TUt+JnwbO
  4vHFxhGDBXMLwiNZdedhE9NO3zUorWPEiVNapp/u0agMXAv3RmJsIGeVJerGFay7Eb9RbhTcHOe
  PGl1IetV7J3A9L14OqauMShaFJQUnTXSqS8ldcge/JfgSiWTqE0TjVc0pYMCAwEAAaOBuzCBuDB
  YBggrBgEFBQcBAQRMMEowSAYIKwYBBQUHMAGGPGh0dHA6Ly9pa3MyLXVidW50dS5jeWJlci5lZT
  o4MDgwL2VqYmNhL3B1YmxpY3dlYi9zdGF0dXMvb2NzcDAdBgNVHQ4EFgQU25SlUgQRwFCiraz2e
  uhPUBqpvj0wDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBR3LYkuA7b9+NJlOTE1ItBGGujSCTAO
  BgNVHQ8BAf8EBAMCBeAwDQYJKoZIhvcNAQEFBQADggEBAFFWRyInsq/jKrW20BKzRr2KAAnE2nD
  VmZLFfcv7ZwrLOOJYkHxdPEfkcXcwJy4B1KJdvm0+1FlgfoKgDiUjTRbXraXmyUwAL5s5yMr9wF
  wu9N9JL6IwchMNT6S5zwA+iioLMQbHAMfwXXSS/Vp7aUxmejK4XbNtehsukalD7S3ILAK7dtamP
  r0YvRqUBbj4k9zD60gVU13jmACr/JuSXI4JxyoiFdUNDdtQbiiGOsrOuLmc/WbzXNo7iN/zhwEM
  JNJThtyGYthhiYeZKT+0B5Yy/sARkinWqLpUwddf+plfH+4HP2akrt8uoHSZXKKOmN8IlXgN89L
  PVBC+oSltnhY=")
end
  
def create_test_data
  # System parameters
  SystemParameter.create(key: SystemParameter::SDSB_INSTANCE, 
    value: "EE")
  SystemParameter.create(key: SystemParameter::CONF_SIGN_KEY_ID, 
    value: "A1937F73EF35A5B733E6FBECA7B850094373BC5D")
  SystemParameter.create(key: SystemParameter::CONF_SIGN_ALGO_ID, 
    value: "SHA-256")
  
  SystemParameter.create(key: SystemParameter::MGMT_SERVICE_URL,
    value: "https://iks2-central.cyber.ee:8443/center-service/")
  SystemParameter.create(key: SystemParameter::MGMT_SERVICE_ID_CLASS,
    value: "BUSINESS")
  SystemParameter.create(key: SystemParameter::MGMT_SERVICE_ID_CODE,
    value: "servicemember2")
    
  # Create PKIs
  pki = Pki.create(name: "pki1", authentication_only: true,
    name_extractor_member_class: "BUSINESS",
    name_extractor_method_name: "foo.bar.baz.NameExtractor.getCommonName")

  top_ca = CaInfo.create(cert: get_cert_bytes)
  intermediate_ca = CaInfo.create(cert: get_cert_bytes)

  ocsp = OcspInfo.create(cert: "ocsp", url: "http://127.0.0.1")
  CaInfo.update(intermediate_ca.id, ocsp_infos: [ocsp])

  Pki.update(pki.id, top_cas: [top_ca], intermediate_cas: [intermediate_ca])

  # Global Groups
  ggroup1 = GlobalGroup.create(group_code: "ggroup1",
    description: "Global group #1")
  ggroup2 = GlobalGroup.create(group_code: "ggroup2",
    description: "Global group #2")

  client1 = ClientId.create(object_type: "CLIENT",
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "client1")
  client2 = ClientId.create(object_type: "CLIENT",
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "client2")
  client3 = ClientId.create(object_type: "CLIENT",
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "client3")
  client4 = ClientId.create(object_type: "CLIENT",
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "client4")
  
  # Creating members for global groups
  ggm1 = GlobalGroupMember.create(group_member: client1, global_group: ggroup1)
  ggm2 = GlobalGroupMember.create(group_member: client2, global_group: ggroup2)
  ggm3 = GlobalGroupMember.create(group_member: client3, global_group: ggroup1)
  ggm4 = GlobalGroupMember.create(group_member: client4, global_group: ggroup1)

  # Create members
  member_class = MemberClass.create(code: "BUSINESS", description: "Business clients")
  member1 = SdsbMember.create(member_code: "consumer", name: "Test Consumer", 
    member_class: member_class, administrative_contact: "foo@bar.baz")
  member2 = SdsbMember.create(member_code: "producer", name: "Test Producer", 
    member_class: member_class, administrative_contact: "foo@bar.baz")

  member3 = SdsbMember.create(member_code: "member3", name: "test Member", 
    member_class: member_class, administrative_contact: "foo@bar.baz")
  subsystem = Subsystem.create(subsystem_code: "subsystem", sdsb_member: member3)

  # Security Servers
  first_server = SecurityServer.create(owner: member1,
      server_code: "firstServerCode", address: "http://first.com")
  second_server = SecurityServer.create(owner: member2,
      server_code: "secondServerCode", address: "http://second.com")

  first_category = SecurityCategory.create(code: "firstCategoryCode",
      description: "Description of first category")
  second_category = SecurityCategory.create(code: "secondCategoryCode",
      description: "Description of second category")

  auth_cert = AuthCert.create(security_server: first_server, certificate: get_cert_bytes)

  SecurityServer.update(first_server.id, security_server_clients: [member2])
  SecurityServer.update(second_server.id, security_server_clients: [subsystem])

  # Add security categories
  SecurityServer.update(first_server.id,
      security_categories: [first_category, second_category])
  SecurityServer.update(second_server.id, security_categories: [first_category])

  # Central services
  central_service = CentralService.create(service_code: "serviceCode")
  target_service = ServiceId.create(object_type: "SERVICE",
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "10",
      service_code: "teenus")

  CentralService.update(central_service.id, target_service: target_service)

  # Add TSPs
  ApprovedTsp.create(name: "Test TSP", url: "http://foo.bar.baz", cert: "foo")

end

def generate_conf
  xml = GlobalConfGenerator.new.generate
  puts "Generated GlobalConf XML: #{xml}"

end

def perform_test
  create_test_data
  generate_conf
ensure
  # Cleanup
  ApprovedTsp.delete_all
  Identifier.delete_all
  CentralService.delete_all
  SecurityServer.delete_all
  SecurityCategory.delete_all
  SdsbMember.delete_all
  MemberClass.delete_all
  GlobalGroupMember.delete_all
  GlobalGroup.delete_all
  Pki.delete_all
  CaInfo.delete_all
  OcspInfo.delete_all
  SystemParameter.delete_all
  AuthCert.delete_all
  Subsystem.delete_all
end

perform_test
