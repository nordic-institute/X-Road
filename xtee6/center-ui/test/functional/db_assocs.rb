# Requires to be run under Rails console.
# XXX Do not run console with option "sandbox", otherwise relations are not found properly for some reason.

def self.get_size_error_message(group, expected_size, actual_size)
  "Size of #{group} members should be #{expected_size}, but is #{actual_size}"
end

# Checks if two arrays contain same elements, order does not matter.
def self.are_arrays_equal(first_array, second_array)
  ((first_array - second_array) + (second_array - first_array)).empty?
end

# Tests - start
def test_one_to_many_associations

  # Creating global groups
  linnuvabrik = GlobalGroup.create(group_code: "linnuvabrik",
    description: "Eriti ohtlik grupeering")
  nahatehas = GlobalGroup.create(group_code: "nahatehas",
    description: "Siin tehakse nahka")

  kallekurg_id = ClientId.create(object_type: "CLIENT",\
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "kallekurg")
  kauponahk_id = ClientId.create(object_type: "CLIENT",\
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "kauponahk")
  martinlind_id = ClientId.create(object_type: "CLIENT",\
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "martinlind")
  olgerdharakas_id = ClientId.create(object_type: "CLIENT",\
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "olgerdharakas")
    
  # Creating members for global groups
  kallekurg = GlobalGroupMember.create(group_member_id: kallekurg_id,
    global_group: linnuvabrik)
  kauponahk = GlobalGroupMember.create(group_member_id: kauponahk_id,
    global_group: nahatehas)
  martinlind = GlobalGroupMember.create(group_member_id: martinlind_id,
    global_group: linnuvabrik)
  olgerdharakas = GlobalGroupMember.create(group_member_id: olgerdharakas_id,
    global_group: linnuvabrik)

  puts "Objects created into database"

  # Validating Linnuvabrik
  expected_linnuvabrik_members = [kallekurg, martinlind, olgerdharakas]

  linnuvabrik_from_db = GlobalGroup.where(group_code: "linnuvabrik").first
  puts "Linnuvabrik from db: #{linnuvabrik_from_db.inspect}"

  linnuvabrik_members_from_db = linnuvabrik_from_db.global_group_members
  puts "Linnuvabrik members from db: #{linnuvabrik_members_from_db}"

  if !are_arrays_equal(expected_linnuvabrik_members, linnuvabrik_members_from_db)
    raise "Arrays of 'linnuvabrik' do not contain equal content."
  end

  # Validating Nahatehas
  expected_nahatehas_members = [kauponahk]

  nahatehas_from_db = GlobalGroup.where(group_code: "nahatehas").first
  puts "Nahatehas from db: #{nahatehas_from_db.inspect}"

  nahatehas_members_from_db = nahatehas_from_db.global_group_members
  puts "Nahatehas members from db: #{nahatehas_members_from_db}"

  if !are_arrays_equal(expected_nahatehas_members, nahatehas_members_from_db)
    raise "Arrays of 'nahatehas' do not contain equal content."
  end
ensure
  GlobalGroupMember.delete_all
  GlobalGroup.delete_all
end

def test_multiple_pki_ca_relations
  pki = Pki.create(name: "daPki", authentication_only: true,
    name_extractor_member_class: "com.example.Klass",
    name_extractor_method_name: "meetod")

  top_ca = CaInfo.create(cert: "topCa")
  intermediate_ca = CaInfo.create(cert: "intermediateCa")

  Pki.update(pki.id, top_cas: [top_ca], intermediate_cas: [intermediate_ca])
  
  # Validate
  if pki.top_cas.size != 1 || pki.intermediate_cas.size != 1
    raise "Certs are not included properly"
  end

  top_ca_bytes_from_db = pki.top_cas[0].cert

  intermediate_ca_bytes_from_db = pki.intermediate_cas[0].cert

  if "topCa" != top_ca_bytes_from_db\
      || "intermediateCa" != intermediate_ca_bytes_from_db
    raise "Cert bytes have no proper content"
  end

  puts "Multiple PKI-CA relations test accomplished successfully"
ensure
  Pki.delete_all
  CaInfo.delete_all
end

def test_many_to_many_assocs
  # sdsb_member_id refers to nothing in this test, it is added, because it is
  # mandatory for SecurityServer.
  member_class = MemberClass.create(:code => 'INT', :description => "Desc")
  owner = SdsbMember.create(
      :member_class => member_class, 
      :name => "Theserver",
      :administrative_contact => "a@b.com",
      :member_code => "1010011")
  
  first_server = SecurityServer.create(:owner => owner,
      server_code: "firstServerCode", address: "first.com")
  second_server = SecurityServer.create(:owner => owner,
      server_code: "secondServerCode", address: "second.com")

  first_category = SecurityCategory.create(code: "firstCategoryCode",\
      description: "Description of first category")
  second_category = SecurityCategory.create(code: "secondCategoryCode",\
      description: "Description of second category")

  # Add security categories
  SecurityServer.update(first_server.id,
      security_categories: [first_category, second_category])
  SecurityServer.update(second_server.id, security_categories: [first_category])

  # Verify with assocs
  expected_first_server_categories = [first_category, second_category]
  expected_second_server_categories = [first_category]


  if !are_arrays_equal(expected_first_server_categories,\
      first_server.security_categories)\
      || !are_arrays_equal(expected_second_server_categories,\
      second_server.security_categories)
    raise "Servers do not contain proper security categories"
  end

  expected_first_category_servers = [first_server, second_server]
  expected_second_category_servers = [first_server]

  if !are_arrays_equal(expected_first_category_servers,\
      first_category.security_servers)\
      || !are_arrays_equal(expected_second_category_servers,\
      second_category.security_servers)
    raise "Categories do not contain proper security servers"
  end

  # Remove all assocs
  SecurityServer.update(first_server.id, :security_categories => [])
  SecurityServer.update(second_server.id, :security_categories => [])

  no_of_assocs = SecurityServersSecurityCategory.count
  if no_of_assocs != 0
    raise "There must be 0 assocs, but is #{no_of_assocs}"
  end

  puts "Many-to-many associations test accomplished successfully"
ensure
  MemberClass.delete_all
  SdsbMember.delete_all
  SecurityServer.delete_all
  SecurityCategory.delete_all
end

def test_single_table_inheritance
  puts "Starting test for single table inheritance"

  central_service = CentralService.create(service_code: "serviceCode")
  target_service_id = ServiceId.create(object_type: "SERVICE",\
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "10",\
      service_code: "teenus")

  # FIXME: Why it does not manage to update?
  CentralService.update(\
      central_service.id, target_service: target_service_id)

  global_group_member = GlobalGroupMember.create(global_group_id: 7)

  group_member_id = ClientId.create(object_type: "CLIENT",\
      sdsb_instance: "EE", member_class: "BUSINESS", member_code: "11")

  GlobalGroupMember.update(\
      global_group_member.id, group_member_id: group_member_id)

  # Verify
  first_service_id = ServiceId.where(member_code: "10").first
  first_client_id = ClientId.where(member_code: "11").first

  raise "Incorrect service"\
      if first_service_id.member_code != central_service.target_service.member_code
  raise "Incorrect client"\
      if first_client_id.member_code != global_group_member.group_member.member_code

  puts "Test for single table inheritance accomplished successfully"
ensure
  Identifier.delete_all
  CentralService.delete_all
  GlobalGroupMember.delete_all
end
# Tests - end

puts "Starting the test of the database..."

test_one_to_many_associations
test_multiple_pki_ca_relations
test_many_to_many_assocs
test_single_table_inheritance

puts "Database test accomplished successfully"
