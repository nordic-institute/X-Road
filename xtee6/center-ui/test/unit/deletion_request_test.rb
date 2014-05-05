require 'test_helper'

class RequestProcessingTest < ActiveSupport::TestCase

  test "Cancel waiting ClientRegRequest when deletion request sent" do
    # Given
    client_id_registration = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_registration = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    client_id_deletion = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_deletion = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    request_registration = ClientRegRequest.new(
        :security_server => server_id_registration,
        :sec_serv_user => client_id_registration,
        :origin => Request::SECURITY_SERVER)

    request_deletion = ClientDeletionRequest.new(
        :security_server => server_id_deletion,
        :sec_serv_user => client_id_deletion,
        :origin => Request::SECURITY_SERVER)

    # When
    request_registration.register()
    request_deletion.register()

    # Then
    saved_processing = RequestProcessing.all.first

    assert_equal(RequestProcessing::CANCELED, saved_processing.status)
  end

  test "Cancel waiting AuthCertRegRequest when deletion request sent" do
    # Given
    server_id_registration =  SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")
    cert_registration = "--authCertBytes"

    server_id_deletion =  SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")
    cert_deletion = "--authCertBytes"

    request_registration = AuthCertRegRequest.new(
        :security_server => server_id_registration,
        :auth_cert => cert_registration,
        :address => "192.168.7.55",
        :origin => Request::CENTER)

    request_deletion = AuthCertDeletionRequest.new(
        :security_server => server_id_deletion,
        :auth_cert => cert_deletion,
        :origin => Request::CENTER)

    # When
    request_registration.register()
    request_deletion.register()

    # Then
    saved_processing = RequestProcessing.all.first
    saved_auth_cert_reg_request = AuthCertRegRequest.first

    assert_equal(RequestProcessing::CANCELED, saved_processing.status)

    assert_equal(1, AuthCertDeletionRequest.all.size)

    deletion_request = AuthCertDeletionRequest.first
    assert_equal(deletion_request.id, 
        saved_auth_cert_reg_request.get_canceling_request_id())
  end

  test "Do not change ClientRegRequest as origins are different" do
    # Given
    client_id_registration = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_registration = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    client_id_deletion = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_deletion = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    request_registration = ClientRegRequest.new(
        :security_server => server_id_registration,
        :sec_serv_user => client_id_registration,
        :origin => Request::SECURITY_SERVER)

    request_deletion = ClientDeletionRequest.new(
        :security_server => server_id_deletion,
        :sec_serv_user => client_id_deletion,
        :origin => Request::CENTER)

    # When
    request_registration.register()
    request_deletion.register()

    # Then
    saved_processing = RequestProcessing.all.first

    assert_equal(RequestProcessing::WAITING, saved_processing.status)
  end

  test "Cancel client reg request" do
    # Given
    client_id_registration = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_registration = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    request_registration = ClientRegRequest.new(
        :security_server => server_id_registration,
        :sec_serv_user => client_id_registration,
        :origin => Request::CENTER)

    # When
    request_registration.register()
    saved_client_reg_request = ClientRegRequest.all.first
    ClientRegRequest.cancel(saved_client_reg_request.id)

    # Then
    processing_status = saved_client_reg_request.request_processing.status
    assert_equal(RequestProcessing::CANCELED, processing_status)

    assert_equal(1, ClientDeletionRequest.all.size)

    deletion_request = ClientDeletionRequest.first
    assert_equal(deletion_request.id, 
        saved_client_reg_request.get_canceling_request_id())
  end

  test "Fail to cancel client reg request from security server" do
    # Given
    client_id_registration = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_registration = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    request_registration = ClientRegRequest.new(
        :security_server => server_id_registration,
        :sec_serv_user => client_id_registration,
        :origin => Request::SECURITY_SERVER) #Inadequate origin

    # When/then
    request_registration.register()
    saved_client_reg_request = ClientRegRequest.all.first

    assert_raises(RuntimeError) do
      ClientRegRequest.cancel(saved_client_reg_request.id)
    end

  end

  test "Cancel auth cert reg request" do
    # Given
    server_id_registration =  SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")
    cert_registration = "--authCertBytes"

    request_registration = AuthCertRegRequest.new(
        :security_server => server_id_registration,
        :auth_cert => cert_registration,
        :address => "192.168.7.55",
        :origin => Request::CENTER)

    # When
    request_registration.register()
    saved_auth_cert_reg_request = AuthCertRegRequest.all.first
    AuthCertRegRequest.cancel(saved_auth_cert_reg_request.id)

    # Then
    processing_status = saved_auth_cert_reg_request.request_processing.status
    assert_equal(RequestProcessing::CANCELED, processing_status)

    assert_equal(1, AuthCertDeletionRequest.all.size)
  end

  test "Raise error when trying to cancel approved request" do
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

    request_from_security_server.register()
    request_from_center.register()

    # When/then
    assert_raises(RuntimeError) do
        first_saved_request = ClientRegRequest.first
        ClientRegRequest.cancel(first_saved_request.id)
    end

    # Then
  end

  test "Successfully delete AuthCertDeletionRequest for not "\
      "yet existing security server" do
    # Given
    server_id_registration =  SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "not_existing_server")
    cert_registration = "--authCertBytes"

    server_id_deletion =  SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "not_existing_server")
    cert_deletion = "--authCertBytes"

    request_registration = AuthCertRegRequest.new(
        :security_server => server_id_registration,
        :auth_cert => cert_registration,
        :address => "192.168.7.55",
        :origin => Request::CENTER)

    request_deletion = AuthCertDeletionRequest.new(
        :security_server => server_id_deletion,
        :auth_cert => cert_deletion,
        :origin => Request::CENTER)

    # When
    request_registration.register()
    request_deletion.register()

    # Then
    saved_processing = RequestProcessing.all.first

    assert_equal(RequestProcessing::CANCELED, saved_processing.status)
  end
end
