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
