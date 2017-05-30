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

class RequestProcessingTest < ActiveSupport::TestCase

  test "Revoke waiting ClientRegRequest when deletion request sent" do
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

    assert_equal(RequestProcessing::REVOKED, saved_processing.status)
  end

  test "Revoke waiting AuthCertRegRequest when deletion request sent" do
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
    member_in_vallavalitsused_name = 
        "This member should belong to group 'vallavalitsused'"
    
    saved_processing = RequestProcessing.all.first
    saved_auth_cert_reg_request = AuthCertRegRequest.first

    assert_equal(member_in_vallavalitsused_name,
        saved_auth_cert_reg_request.server_owner_name)

    assert_equal(RequestProcessing::REVOKED, saved_processing.status)

    assert_equal(1, AuthCertDeletionRequest.all.size)

    deletion_request = AuthCertDeletionRequest.first
    assert_equal(deletion_request.id, 
        saved_auth_cert_reg_request.get_revoking_request_id())
    assert_equal(member_in_vallavalitsused_name,
        deletion_request.server_owner_name)
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

  test "Revoke client reg request" do
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
    ClientRegRequest.revoke(saved_client_reg_request.id)

    # Then
    processing_status = saved_client_reg_request.request_processing.status
    assert_equal(RequestProcessing::REVOKED, processing_status)

    assert_equal(1, ClientDeletionRequest.all.size)

    deletion_request = ClientDeletionRequest.first
    assert_equal(deletion_request.id, 
        saved_client_reg_request.get_revoking_request_id())
  end

  test "Fail to revoke client reg request from security server" do
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
      ClientRegRequest.revoke(saved_client_reg_request.id)
    end

  end

  test "Revoke auth cert reg request" do
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
    AuthCertRegRequest.revoke(saved_auth_cert_reg_request.id)

    # Then
    processing_status = saved_auth_cert_reg_request.request_processing.status
    assert_equal(RequestProcessing::REVOKED, processing_status)

    assert_equal(1, AuthCertDeletionRequest.all.size)
  end

  test "Raise error when trying to revoke approved request" do
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
        ClientRegRequest.revoke(first_saved_request.id)
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

    assert_equal(RequestProcessing::REVOKED, saved_processing.status)
  end
end
