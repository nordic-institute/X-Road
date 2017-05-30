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

require 'test_helper'

class SecurityServerClientTest < ActiveSupport::TestCase

  def setup
    @group_id = ActiveRecord::Fixtures.identify(:vallavalitsused)
  end

  test "Get all clients with offset and order" do
    # Given
    query_params = ListQueryParams.new(
        "identifiers.member_code","desc", 1, 2)

    # When
    result = SecurityServerClient.get_clients(query_params)

    # Then
    assert_equal(2, result.size)

    first_client = result[0]
    second_client = result[1]

    assert_not_nil(first_client[:name])
    assert_not_nil(second_client[:name])

    first_identifier = first_client[:identifier]
    second_identifier = second_client[:identifier]

    assert_equal("member_out_of_vallavalitsused", first_identifier.member_code)
    assert_equal("member_in_vallavalitsused", second_identifier.member_code)
  end

  test "Search amongst all clients" do
    # Given
    query_params = ListQueryParams.new(
        "identifiers.subsystem_code","asc", 0, 10, "subsystem")

    # When
    result = SecurityServerClient.get_clients(query_params)

    # Then
    assert_equal(3, result.size)

    first_identifier = result[0][:identifier]
    second_identifier = result[1][:identifier]
    third_identifier = result[2][:identifier]

    assert_equal("subsystem_as_server_client",
        first_identifier.subsystem_code)
    assert_equal("subsystem_in_vallavalitsused",
        second_identifier.subsystem_code)
    assert_equal("subsystem_out_of_vallavalitsused",
        third_identifier.subsystem_code)
  end

  test "Get all clients count" do
    assert_equal(6, SecurityServerClient.get_clients_count(""))
  end

  test "Search name from all clients" do
    # Given
    query_params = ListQueryParams.new(
        "identifiers.object_type","asc", 0, 10, "not belong")

    # When
    result = SecurityServerClient.get_clients(query_params)

    # Then
    assert_equal(2, result.size)

    # Member
    member = result[0]
    member_id = member[:identifier]
    member_name = member[:name]

    assert_equal("This member should NOT belong to group 'vallavalitsused'",
        member_name)
    assert_equal("member_out_of_vallavalitsused", member_id.member_code)
    assert_nil(member_id.subsystem_code)

    #Subsystem
    subsystem = result[1]
    subsystem_id = subsystem[:identifier]
    subsystem_name = subsystem[:name]

    assert_equal("This member should NOT belong to group 'vallavalitsused'",
        subsystem_name)
    assert_equal("member_out_of_vallavalitsused", subsystem_id.member_code)
    assert_equal("subsystem_in_vallavalitsused", subsystem_id.subsystem_code)
  end

  test "Get searchable clients count" do
    assert_equal(2, SecurityServerClient.get_clients_count("not belong"))
  end

  test "Search only clients not in global group" do
    #Given
    query_params = ListQueryParams.new(
        "identifiers.object_type","asc", 0, 10)

    #When
    result = SecurityServerClient.
        get_remaining_clients_for_group(@group_id, query_params)

    #Then
    assert_equal(4, result.size)

    first_member = result[0]
    first_member_id = first_member[:identifier]
    expected_server_codes =
        ["member_out_of_vallavalitsused", "member_as_server_client"]

    expected_server_codes.delete_if { |c| c == first_member_id.member_code}
    assert_nil(first_member_id.subsystem_code)

    second_member = result[1]
    second_member_id = second_member[:identifier]

    expected_server_codes.delete_if { |c| c == second_member_id.member_code}
    assert_nil(second_member_id.subsystem_code)

    assert_equal(
        0,
        expected_server_codes.size,
        "Following server codes not found: '#{expected_server_codes}'")
  end

  test "Get count of all clients not in group" do
    assert_equal(4,
        SecurityServerClient.get_remaining_clients_count(@group_id, ""))
  end

  test "Get count of searchable clients not in group" do
    assert_equal(1, SecurityServerClient.get_remaining_clients_count(@group_id,
        "not belong"))
  end

  test "Get remaining members for empty group" do
    empty_group_id = ActiveRecord::Fixtures.identify(:tyhigrupp)
    assert_equal(6,
        SecurityServerClient.get_remaining_clients_count(empty_group_id, ""))
  end

  test "Perform advanced search" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_client_names.name","asc", 0, 10)

    advanced_search_params = AdvancedSearchParams.new(
      {
        :name => "should belong",
        :member_code => "member_IN",
        :subsystem_code => "subsystem_out_of_vallavalitsused",
      }
    )

    # When
    result = SecurityServerClient.
        get_clients(query_params, advanced_search_params)

    # Then
    assert_equal(1, result.size)

    client = result[0]
    name = client[:name]
    client_id = client[:identifier]

    assert_equal("This member should belong to group 'vallavalitsused'", name)
    assert_equal("subsystem_out_of_vallavalitsused", client_id.subsystem_code)
  end

  test "Perform remaining members advanced search for group" do
    # Given
    query_params = ListQueryParams.new(
        "identifiers.member_code","desc", 0, 10)

    advanced_search_params = AdvancedSearchParams.new(
      {
        :name => "should belong",
        :member_code => "member_in",
        :subsystem_code => "subsystem_out_of_vallavalitsused",
        :object_type => "SUBSYSTEM"
      }
    )

    # When
    result = SecurityServerClient.
        get_remaining_clients_for_group(
            @group_id, query_params, advanced_search_params)

    # Then
    assert_equal(1, result.size)

    client = result[0]
    name = client[:name]
    client_id = client[:identifier]

    assert_equal("This member should belong to group 'vallavalitsused'",
        name)
    assert_equal("subsystem_out_of_vallavalitsused", client_id.subsystem_code)
    assert_equal("SUBSYSTEM", client_id.object_type)
  end

  test "Should order by name alphabetically" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_client_names.name","desc", 1, 10)

    # When
    result = SecurityServerClient.
        get_clients(query_params)

    # Then
    assert_equal(5, result.size)

    # String ordering in SQLite seems to be case sensitive. As with Postgres
    # string ordering is insensitive, this test may fail when using PostgreSQL
    # in test environment.
    assert_equal("This member should belong to group 'vallavalitsused'",
        result[0][:name])
    assert_equal("This member should NOT belong to group 'vallavalitsused'",
        result[1][:name])
    assert_equal("This member should NOT belong to group 'vallavalitsused'",
        result[2][:name])
  end

  test "Should remove related global group memberships when member destroyed" do
    # Given
    member_to_destroy = XroadMember.create!(
      :member_class => get_riigiasutus(),
      :member_code => "deletable",
      :name => "DeletableName",
      :administrative_contact => "a@b.com")

    first_group = get_tyhigrupp()
    group_member_id = ClientId.from_parts("EE", "riigiasutus", "deletable")
    first_group.add_member(group_member_id)

    second_group = get_vallavalitsused()
    second_group.add_member(group_member_id.clean_copy())

    # When
    XroadMember.destroy(member_to_destroy)

    # Then
    assert(!first_group.has_member?(group_member_id.clean_copy()),
      "Deleted member should not belong to any group!")

    assert(!second_group.has_member?(group_member_id.clean_copy()),
      "Deleted member should not belong to any group!")
  end

  test "Should create client deletion requests when member destroyed" do
    # Given
    server_to_destroy = get_security_server()

    member_to_destroy = XroadMember.create!(
      :member_class => get_riigiasutus(),
      :member_code => "memberToDestroy",
      :name => "DeletableName",
      :administrative_contact => "a@b.com",
      :security_servers => [server_to_destroy])

    subsystem_to_destroy = Subsystem.create!(
      :subsystem_code => "subsystemToDestroy",
      :xroad_member => member_to_destroy,
      :security_servers => [server_to_destroy])

    # When
    XroadMember.destroy(member_to_destroy)

    # Then
    client_deletion_requests = ClientDeletionRequest.all
    assert_equal(2, client_deletion_requests.size)

    member_in_vallavalitsused_name =
        "This member should belong to group 'vallavalitsused'"

    member_request = client_deletion_requests[0]
    assert_equal("securityServer", member_request.security_server.server_code)
    assert_equal("memberToDestroy", member_request.sec_serv_user.member_code)
    assert_equal("memberToDestroy", member_request.sec_serv_user.member_code)
    assert_equal(member_in_vallavalitsused_name,
        member_request.server_owner_name)
    assert_equal("DeletableName", member_request.server_user_name)

    subsystem_request = client_deletion_requests[1]
    assert_equal("subsystemToDestroy",
        subsystem_request.sec_serv_user.subsystem_code)
    assert_equal(member_in_vallavalitsused_name,
        subsystem_request.server_owner_name)
    assert_equal("DeletableName", subsystem_request.server_user_name)
  end

  test "Should get addable clients for the server" do
    # Given
    query_params = ListQueryParams.new(
            "identifiers.subsystem_code","asc", 0, 10, "")

    # When
    clients = SecurityServerClient.get_addable_clients_for_server(
        "securityServer", query_params, nil)

    # Then
    assert_equal(5, clients.size)

    clients.each do |each|
      identifier = each[:identifier]
      assert_not_equal("subsystem_as_server_client", identifier.subsystem_code)
    end
  end

  test "Should get addable clients count for the server" do
    assert_equal(5,
        SecurityServerClient.get_addable_clients_count("securityServer", ""))
  end

  private

  def get_security_server
    id = ActiveRecord::Fixtures.identify(:security_server)
    SecurityServer.find(id)
  end

  def get_tyhigrupp
    id = ActiveRecord::Fixtures.identify(:tyhigrupp)
    GlobalGroup.find(id)
  end

  def get_vallavalitsused
    id = ActiveRecord::Fixtures.identify(:vallavalitsused)
    GlobalGroup.find(id)
  end
end
