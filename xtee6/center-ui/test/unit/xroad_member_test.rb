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

class XroadMemberTest < ActiveSupport::TestCase

  test "Should load all requests" do
    # Given
    query_params = ListQueryParams.new(
        "requests.created_at", "desc", 0, 10)

    # When
    requests = XroadMember.get_management_requests(
        "riigiasutus", "member_in_vallavalitsused", query_params)

    # Then
    assert_equal(2, requests.size)
  end

  test "Should find requests with offset" do
    # Given
    query_params = ListQueryParams.new(
        "requests.created_at", "desc", 1, 10)

    # When
    requests = XroadMember.get_management_requests(
        "riigiasutus", "member_in_vallavalitsused", query_params)

    # Then
    assert_equal(1, requests.size)

    request = requests[0]
    assert_equal("CENTER", request.origin)
    assert_equal("member_in_vallavalitsused", request.security_server.member_code)
  end

  test "Should find requests with limit" do
    # Given
    query_params = ListQueryParams.new(
        "requests.created_at", "desc", 0, 1)

    # When
    requests = XroadMember.get_management_requests(
        "riigiasutus", "member_in_vallavalitsused", query_params)

    # Then
    assert_equal(1, requests.size)

    request = requests[0]
    assert_equal("SECURITY_SERVER", request.origin)
    assert_equal("member_in_vallavalitsused", request.sec_serv_user.member_code)
  end

  test "Should update request server owner name when member name changed" do
    # Given
    member = XroadMember.where(:member_code => "member_in_vallavalitsused").first
    new_name = "New name"

    # When
    member.update_attributes!(:name => new_name)

    # Then
    updated_request = Request.where(
        :server_owner_class => "riigiasutus",
        :server_owner_code => "member_in_vallavalitsused").first

    assert_equal(new_name, updated_request.server_owner_name)
  end

  test "Should update request server user name when member name changed" do
    # Given
    member = XroadMember.where(:member_code => "member_out_of_vallavalitsused").first
    new_name = "New name"

    # When
    member.update_attributes!(:name => new_name)

    # Then
    updated_request = Request.where(
        :server_owner_class => "riigiasutus",
        :server_owner_code => "member_in_vallavalitsused").first

    assert_equal(new_name, updated_request.server_user_name)
  end

  test "Should preserve server owner name when owner deleted" do
    # Given
    member = XroadMember.where(:member_code => "member_in_vallavalitsused").first

    # When
    member.destroy

    # Then
    last_name = "This member should belong to group 'vallavalitsused'"
    updated_request = Request.where(
        :server_owner_class => "riigiasutus",
        :server_owner_code => "member_in_vallavalitsused").first

    assert_equal(last_name, updated_request.server_owner_name)
  end

  test "Should preserve server user name when user deleted" do
    # Given
    member = XroadMember.where(:member_code => "member_out_of_vallavalitsused").first

    # When
    member.destroy

    # Then
    last_name = "This member should NOT belong to group 'vallavalitsused'"
    updated_request = Request.where(
        :server_owner_class => "riigiasutus",
        :server_owner_code => "member_in_vallavalitsused").first

    assert_equal(last_name, updated_request.server_user_name)
  end
end
