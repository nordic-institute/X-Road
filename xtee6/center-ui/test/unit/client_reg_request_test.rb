require 'test_helper'

class AuthCertRegRequestTest < ActiveSupport::TestCase

  test "Should update server client name for registration request" do
    # Given
    client_id = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_out_of_vallavalitsused", "extraSecureServer")

    request = ClientRegRequest.new(
        :security_server => server_id,
        :sec_serv_user => client_id,
        :origin => Request::SECURITY_SERVER)

    # When
    request.register

    # Then
    saved_request = ClientRegRequest.where(
        :origin => Request::SECURITY_SERVER).last

    assert_equal(
        "This member should NOT belong to group 'vallavalitsused'",
        saved_request.server_owner_name)
    assert_equal(
        "This member should belong to group 'vallavalitsused'",
        saved_request.server_user_name)
  end

  test "Should update server client name for deletion request" do
    # Given
    client_id_registration = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_registration = SecurityServerId.from_parts(
        "EE",
        "riigiasutus",
        "member_out_of_vallavalitsused",
        "extraSecureServer")

    request_registration = ClientRegRequest.new(
        :security_server => server_id_registration,
        :sec_serv_user => client_id_registration,
        :origin => Request::SECURITY_SERVER)

    client_id_deletion = client_id_registration.clean_copy
    server_id_deletion = server_id_registration.clean_copy

    request_deletion = ClientDeletionRequest.new(
        :security_server => server_id_deletion,
        :sec_serv_user => client_id_deletion,
        :origin => Request::SECURITY_SERVER)

    # When
    request_registration.register
    request_deletion.register

    # Then
    saved_request = ClientDeletionRequest.where(
      :origin => Request::SECURITY_SERVER).last

    assert_equal(
        "This member should NOT belong to group 'vallavalitsused'",
        saved_request.server_owner_name)
    assert_equal(
        "This member should belong to group 'vallavalitsused'",
        saved_request.server_user_name)
  end
end
