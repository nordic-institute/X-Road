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

  def setup
    # We need no requests here in advance.
    Request.delete_all()
    RequestProcessing.delete_all()
  end

  test "Register paired requests" do
    # Given
    clients_count_before = get_server_clients_count()

    # When
    get_request_from_security_server().register()
    get_request_from_center().register()

    # Then
    saved_processing = RequestProcessing.all.first

    assert_equal("SUBMITTED FOR APPROVAL", saved_processing.status)
    assert(get_server_clients_count() == clients_count_before,
      "Server clients count must remain unchanged")
  end

  test "Fail to approve requests when not submitted for approval" do
    # Given
    get_request_from_security_server().register()

    # When/then
    assert_raises(RuntimeError) do
      RequestProcessing.first().approve()
    end
  end

  test "Approve requests successfully" do
    # Given
    clients_count_before = get_server_clients_count()

    # When
    get_request_from_security_server().register()
    get_request_from_center().register()
    RequestProcessing.first().approve()

    # Then
    saved_processing = RequestProcessing.first()
    assert_equal("APPROVED", saved_processing.status)

    clients_count_increase = get_server_clients_count() - clients_count_before
    assert_equal(1, clients_count_increase)

    # The next request for the same data must fail.
    assert_raises(InvalidClientRegRequestException) do
      get_request_from_security_server().register()
    end
  end

  test "Fail to decline requests if not submitted for approval" do
    # Given
    get_request_from_security_server().register()

    # When/then
    assert_raises(RuntimeError) do
      RequestProcessing.first().decline()
    end
  end

  test "decline requests successfully" do
    # Given/when
    get_request_from_security_server().register()
    get_request_from_center().register()
    RequestProcessing.first().decline()

    # Then
    saved_processing = RequestProcessing.first()
    assert_equal("DECLINED", saved_processing.status)
  end

  test "Raise error when auth cert request with same cert is waiting" do
    # Given
    waiting_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "firstServer"),
        :auth_cert => read_testorg_cert(),
        :address => "www.waitingcert.com",
        :origin => Request::SECURITY_SERVER)

    waiting_auth_cert_reg_request.register()

    same_cert_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "secondServer"),
        :auth_cert => read_testorg_cert(),
        :address => "www.waitingcert.com",
        :origin => Request::SECURITY_SERVER)

    # When/then
    assert_raises(InvalidAuthCertRegRequestException) do
      same_cert_auth_cert_reg_request.register()
    end
  end

  test "Raise error when client reg request with same data is waiting" do
    # Given
    waiting_client_reg_request = ClientRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "firstServer"),
        :sec_serv_user => ClientId.from_parts(
            "EE",
            "riigiasutus",
            "member_out_of_vallavalitsused"),
        :origin => Request::SECURITY_SERVER)

    waiting_client_reg_request.register()

    second_client_reg_request = ClientRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "firstServer"),
        :sec_serv_user => ClientId.from_parts(
            "EE",
            "riigiasutus",
            "member_out_of_vallavalitsused"),
        :origin => Request::SECURITY_SERVER)
    # When/then
    assert_raises(RuntimeError) do
      second_client_reg_request.register()
    end
  end


  test "Raise error when auth cert request with same cert is submitted for approval" do
    # Given
    first_submitted_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "firstServer"),
        :auth_cert => read_testorg_cert(),
        :address => "www.submittedcert.com",
        :origin => Request::SECURITY_SERVER)

    second_submitted_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "firstServer"),
        :auth_cert => read_testorg_cert(),
        :address => "www.submittedcert.com",
        :origin => Request::CENTER)

    new_auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "secondServer"),
        :auth_cert => read_testorg_cert(),
        :address => "www.submittedcert.com",
        :origin => Request::CENTER)

    first_submitted_auth_cert_reg_request.register()
    second_submitted_auth_cert_reg_request.register()

    # When/then
    assert_raises(InvalidAuthCertRegRequestException) do
      new_auth_cert_reg_request.register()
    end
  end

  test "Raise error when client reg request with same data is submitted for approval" do
    # Given
    first_client_reg_request = ClientRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "securityServer"),
        :sec_serv_user => ClientId.from_parts(
            "EE",
            "riigiasutus",
            "member_out_of_vallavalitsused",
            "subsystem_in_vallavalitsused"),
        :origin => Request::SECURITY_SERVER)

    second_client_reg_request = ClientRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "securityServer"),
        :sec_serv_user => ClientId.from_parts(
            "EE",
            "riigiasutus",
            "member_out_of_vallavalitsused",
            "subsystem_in_vallavalitsused"),
        :origin => Request::CENTER)

    third_client_reg_request = ClientRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "securityServer"),
        :sec_serv_user => ClientId.from_parts(
            "EE",
            "riigiasutus",
            "member_out_of_vallavalitsused",
            "subsystem_in_vallavalitsused"),
        :origin => Request::SECURITY_SERVER)

    first_client_reg_request.register()
    second_client_reg_request.register()

    # When/then
    assert_raises(InvalidClientRegRequestException) do
      third_client_reg_request.register()
    end
  end

  test "Raise error when security server with the same cert already exists" do
    # Given
    server = SecurityServer.where(:server_code => "securityServer").first
    AuthCert.create!(
        :security_server => server,
        :cert => read_testorg_cert())

    existing_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "firstServer"),
        :auth_cert => read_testorg_cert(),
        :address => "www.submittedcert.com",
        :origin => Request::CENTER)

    # In this case we must skip registration validations as this is simplified
    # way to mimic already existing request.
    existing_request.save!

    new_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            "EE",
            "riigiasutus",
            "member_in_vallavalitsused",
            "firstServer"),
        :auth_cert => read_testorg_cert(),
        :address => "www.submittedcert.com",
        :origin => Request::CENTER)

    # When/then
    assert_raises(InvalidAuthCertRegRequestException) do
      new_request.register()
    end
  end

  private

  def get_request_from_security_server
    client_id_server = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_server = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    return ClientRegRequest.new(
        :security_server => server_id_server,
        :sec_serv_user => client_id_server,
        :origin => Request::SECURITY_SERVER)
  end

  def get_request_from_center
    client_id_center = ClientId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused")
    server_id_center = SecurityServerId.from_parts(
        "EE", "riigiasutus", "member_in_vallavalitsused", "securityServer")

    return ClientRegRequest.new(
        :security_server => server_id_center,
        :sec_serv_user => client_id_center,
        :origin => Request::CENTER)
  end

  def get_server_clients_count
    raw_count = ActiveRecord::Base.connection.execute(
        "SELECT COUNT(*) FROM server_clients")

    return raw_count[0]["COUNT(*)"]
  end
end
