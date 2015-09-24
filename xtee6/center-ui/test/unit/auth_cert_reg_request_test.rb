require 'test_helper'

class AuthCertRegRequestTest < ActiveSupport::TestCase

  def setup
    @testorg_cert = read_testorg_cert()
  end

  test "Should register security server request after central" do
    # Given
    first_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts("EE", "riigiasutus",
            "member_in_vallavalitsused", "securityServer"),
        :auth_cert => @testorg_cert,
        :address => "www.example.com",
        :origin => Request::CENTER)


    second_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts("EE", "riigiasutus",
            "member_in_vallavalitsused", "securityServer"),
        :auth_cert => @testorg_cert,
        :address => "www.example.com",
        :origin => Request::SECURITY_SERVER)

    # When
    first_auth_cert_reg_request.register()
    second_auth_cert_reg_request.register()

    # Then
    saved_requests = AuthCertRegRequest.where(:auth_cert => @testorg_cert)
    assert_equal(2, saved_requests.size)
  end

  test "Should register central request after security server" do
    # Given
    first_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts("EE", "riigiasutus",
            "member_in_vallavalitsused", "securityServer"),
        :auth_cert => @testorg_cert,
        :address => "www.example.com",
        :origin => Request::SECURITY_SERVER)

    second_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts("EE", "riigiasutus",
            "member_in_vallavalitsused", "securityServer"),
        :auth_cert => @testorg_cert,
        :address => "www.example.com",
        :origin => Request::CENTER)

    # When
    first_auth_cert_reg_request.register()
    second_auth_cert_reg_request.register()

    # Then
    saved_requests = AuthCertRegRequest.where(:auth_cert => @testorg_cert)
    assert_equal(2, saved_requests.size)
  end

  test "Should raise error when two requests are identical" do
    # Given
    first_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts("EE", "riigiasutus",
            "member_in_vallavalitsused", "securityServer"),
        :auth_cert => @testorg_cert,
        :address => "www.example.com",
        :origin => Request::CENTER)

    second_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts("EE", "riigiasutus",
            "member_in_vallavalitsused", "securityServer"),
        :auth_cert => @testorg_cert,
        :address => "www.example.com",
        :origin => Request::CENTER)

    # When/then
    assert_raises(InvalidAuthCertRegRequestException) do
      first_auth_cert_reg_request.register()
      second_auth_cert_reg_request.register()
    end
  end

  test "Should add name to the request after sending" do
    # Given
    auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts("EE", "riigiasutus",
            "member_as_server_client", "securityServer"),
        :auth_cert => @testorg_cert,
        :address => "www.example.com",
        :origin => Request::SECURITY_SERVER)

    # When
    auth_cert_reg_request.register()

    # Then
    saved_request = AuthCertRegRequest.where(:auth_cert => @testorg_cert).first
    assert_equal(
        "Testing member as server client",
        saved_request.server_owner_name)
  end

  test "Should add name to the revoking request after sending" do
    # Given
    auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts("EE", "riigiasutus",
            "member_as_server_client", "securityServer"),
        :auth_cert => @testorg_cert,
        :address => "www.example.com",
        :origin => Request::SECURITY_SERVER)

    auth_cert_deletion_request = AuthCertDeletionRequest.new(
        :security_server => SecurityServerId.from_parts("EE", "riigiasutus",
            "member_as_server_client", "securityServer"),
        :auth_cert => @testorg_cert,
        :address => "www.example.com",
        :origin => Request::SECURITY_SERVER)

    # When
    auth_cert_reg_request.register()
    auth_cert_deletion_request.register()

    # Then
    saved_request = AuthCertDeletionRequest.\
        where(:auth_cert => @testorg_cert).first

    assert_equal(
        "Testing member as server client",
        saved_request.server_owner_name)
  end
end
