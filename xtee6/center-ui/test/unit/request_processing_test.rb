require 'test_helper'

class RequestProcessingTest < ActiveSupport::TestCase

  test "Process paired requests" do
    # Given
    client_id_server = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_server = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    client_id_center = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_center = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    request_from_security_server = ClientRegRequest.new(
        :security_server => server_id_server,
        :sec_serv_user => client_id_server,
        :origin => Request::SECURITY_SERVER)

    request_from_center = ClientRegRequest.new(
        :security_server => server_id_center,
        :sec_serv_user => client_id_center,
        :origin => Request::CENTER)

    # When
    request_from_security_server.register()
    request_from_center.register()

    # Then
    saved_processing = RequestProcessing.all.first

    assert_equal("APPROVED", saved_processing.status)
  end
end
