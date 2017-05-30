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

class SecurityServerTest < ActiveSupport::TestCase
  def setup
    @server = {
      :member_class => "riigiasutus",
      :member_code => "member_out_of_vallavalitsused",
      :server_code => "tuumaserver"
    }
  end

  test "Destroy security server clients and add client deletion requests" do
    # Given
    xroad_member_owner = get_owner()
    member_class_riigiasutus = get_riigiasutus()

    xroad_member_client = XroadMember.create!(
      :member_class => member_class_riigiasutus,
      :member_code => "member_client",
      :name => "Owner name",
      :administrative_contact => "a@example.com")

    subsystem_client = Subsystem.create!(
      :xroad_member => xroad_member_client,
      :subsystem_code => "subsystem_client")

    security_server_deletable = SecurityServer.create!(
      :owner => xroad_member_owner,
      :server_code => "security_server_deletable",
      :security_server_clients => [xroad_member_client, subsystem_client])

    # When
    SecurityServer.destroy(security_server_deletable)

    # Then
    client_deletion_requests = ClientDeletionRequest.all
    assert_equal(2, client_deletion_requests.size)

    first_request = client_deletion_requests[0]
    first_client_identifier = first_request.sec_serv_user
    assert_equal("member_client", first_client_identifier.member_code)
    assert_equal("This member should belong to group 'vallavalitsused'",
        first_request.server_owner_name)

    second_request = client_deletion_requests[1]
    second_client_identifier = second_request.sec_serv_user
    assert_equal("member_client", second_client_identifier.member_code)
    assert_equal("subsystem_client", second_client_identifier.subsystem_code)
    assert_equal("This member should belong to group 'vallavalitsused'",
        second_request.server_owner_name)
  end

  test "Should preserve owner name in request after owner deleted" do
    # Given
    owner_member = XroadMember.create!(
      :member_class => get_riigiasutus,
      :member_code => "ownerMember",
      :name => "Owner name",
      :administrative_contact => "a@example.com")

    SecurityServer.create!(
      :owner => owner_member,
      :server_code => "security_server_deletable")

    server_id = SecurityServerId.from_parts(
      "EE", "riigiasutus", "ownerMember", "security_server_deletable")

    AuthCertDeletionRequest.new(
      :security_server => server_id,
      :auth_cert => "==bytes",
      :origin => Request::CENTER).register()

    # When
    XroadMember.destroy(owner_member)

    # Then
    auth_cert_deletion_requests = AuthCertDeletionRequest.all
    assert_equal(1, auth_cert_deletion_requests.size)

    deletion_request = auth_cert_deletion_requests[0]
    assert_equal("Owner name", deletion_request.server_owner_name)
  end

  test "Should register auth cert deletion requests when destroyed" do
    # Given
    server_to_destroy = SecurityServer.create!(
      :owner => get_owner(),
      :server_code => "serverWithAuthCert")

    AuthCert.create!(
      :cert => "--bytes",
      :security_server => server_to_destroy)

    # When
    SecurityServer.destroy(server_to_destroy)

    # Then
    auth_cert_deletion_requests = AuthCertDeletionRequest.all
    assert_equal(1, auth_cert_deletion_requests.size)

    deletion_request = auth_cert_deletion_requests[0]
    assert_equal("serverWithAuthCert",
        deletion_request.security_server.server_code)
    assert_equal("--bytes", deletion_request.auth_cert)

    assert_equal(0, AuthCert.all.size)
  end

  def get_owner
    id = ActiveRecord::Fixtures.identify(:member_in_vallavalitsused)
    XroadMember.find(id)
  end

  test "Should remove owner from owners group if only one owned server" do
    # Given
    deletable_server = SecurityServer.find(
        ActiveRecord::Fixtures.identify(:security_server))

    # When
    SecurityServer.destroy(deletable_server)

    # Then
    owners_group = GlobalGroup.find(
        ActiveRecord::Fixtures.identify(:server_owners))
    assert_equal(1, owners_group.member_count) # 2 was before

    members = owners_group.global_group_members
    assert_equal(1, members.size)

    member = members[0]
    assert_equal("member_out_of_vallavalitsused",
        member.group_member.member_code)
  end

  test "Owner should stay in owners group if owner has more than one server" do
    # Given
    deletable_server = SecurityServer.find(
        ActiveRecord::Fixtures.identify(:tuumaserver))

    # When
    SecurityServer.destroy(deletable_server)

    # Then
    owners_group = GlobalGroup.find(
        ActiveRecord::Fixtures.identify(:server_owners))
    assert_equal(2, owners_group.member_count) # unchanged

    members = owners_group.global_group_members
    assert_equal(2, members.size)

    member1 = members[0]
    assert_equal("member_out_of_vallavalitsused",
        member1.group_member.member_code)

    member2 = members[1]
    assert_equal("member_in_vallavalitsused",
        member2.group_member.member_code)
  end

  test "Should load all requests" do
    # Given
    query_params = ListQueryParams.new(
        "requests.created_at", "desc", 0, 10)

    # When
    requests = SecurityServer.get_management_requests(@server, query_params)

    # Then
    assert_equal(2, requests.size)
  end

  test "Should find requests with offset" do
    # Given
    query_params = ListQueryParams.new(
        "requests.created_at", "desc", 1, 10)

    # When
    requests = SecurityServer.get_management_requests(@server, query_params)

    # Then
    assert_equal(1, requests.size)

    request = requests[0]
    assert_equal("CENTER", request.origin)
    assert_equal("member_out_of_vallavalitsused", request.security_server.member_code)
  end

  test "Should find requests with limit" do
    # Given
    query_params = ListQueryParams.new(
        "requests.created_at", "desc", 0, 1)

    # When
    requests = SecurityServer.get_management_requests(@server, query_params)

    # Then
    assert_equal(1, requests.size)

    request = requests[0]
    assert_equal("SECURITY_SERVER", request.origin)
    assert_equal("member_out_of_vallavalitsused", request.security_server.member_code)
  end

end
