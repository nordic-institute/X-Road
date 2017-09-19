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

class MembersControllerTest < ActionController::TestCase

  test "Should get used servers both for members and subsystems" do
    # Given
    params = {
      'memberClass' => "riigiasutus",
      'memberCode' => "member_as_server_client"
    }

    # When
    get(:used_servers, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)
    actual_used_servers = response_as_json["data"]

    assert_equal(2, actual_used_servers.size())

    actual_server1 = actual_used_servers[0]
    assert_nil(actual_server1["client_subsystem_code"])

    actual_server2 = actual_used_servers[1]
    assert_equal("subsystem_as_server_client",
        actual_server2["client_subsystem_code"])
  end

  test "Should get owned servers for member" do
    # Given
    params = {
      'memberClass' => "riigiasutus",
      'memberCode' => "member_in_vallavalitsused",
    }

    # When
    get(:owned_servers, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)
    actual_owned_servers = response_as_json["data"]

    assert_equal(1, actual_owned_servers.size)
    assert_equal("securityServer", actual_owned_servers[0]["server"])
  end

  test "Should get remaining global groups for member" do
    # Given
    params = {
      'memberClass' => "riigiasutus",
      'memberCode' => "member_in_vallavalitsused",
      'subsystemCode' => ""
    }

    # When
    get(:remaining_global_groups, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)
    remaining_global_groups = response_as_json["data"]

    assert_equal(1, remaining_global_groups.size)

    remaining_group = remaining_global_groups[0]
    assert_equal("tyhjus", remaining_group["code"]);
  end

  test "Should get remaining global groups for member with subsystems" do
    # Given
    params = {
      'memberClass' => "riigiasutus",
      'memberCode' => "member_out_of_vallavalitsused",
      'subsystemCode' => ""
    }

    # When
    get(:remaining_global_groups, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)
    remaining_global_groups = response_as_json["data"]

    assert_equal(2, remaining_global_groups.size)
  end

  test "Should get remaining global groups for subsystem" do
    # Given
    params = {
      'memberClass' => "riigiasutus",
      'memberCode' => "member_out_of_vallavalitsused",
      'subsystemCode' => "subsystem_in_vallavalitsused"
    }

    # When
    get(:remaining_global_groups, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)
    remaining_global_groups = response_as_json["data"]

    assert_equal(1, remaining_global_groups.size)

    remaining_group = remaining_global_groups[0]
    assert_equal("tyhjus", remaining_group["code"]);
  end

  test "Should get management requests for member" do
    # Given
    params = {
      'memberClass' => "riigiasutus",
      'memberCode' => "member_in_vallavalitsused",
      'iDisplayStart' => 1,
      'iDisplayLength' => 100,
      'iSortCol_0' => 2,
      'sSortDir_0' => "desc"
    }

    # When
    get(:management_requests, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)

    requests = response_as_json["aaData"]
    assert_equal(1, requests.size)

    request = requests[0]
    assert_equal("CENTER", request["source"])
  end

  test "Should update names in requests when name changed" do
    # Given
    changed_owner_name = "Changed owner name"
    changed_user_name = "Changed user name"

    # When
    change_member_name("member_out_of_vallavalitsused", changed_owner_name)
    change_member_name("member_in_vallavalitsused", changed_user_name)

    # Then

    edited_request =
        Request.find(ActiveRecord::Fixtures.identify(:request_for_used_server))

    assert_equal(changed_owner_name, edited_request.server_owner_name)
    assert_equal(changed_user_name, edited_request.server_user_name)
  end

  test "Should get subsystem codes of member in alphabetical order" do
    # Given
    params = {
      'memberClass' => "riigiasutus",
      'memberCode' => "member_as_server_client"
    }

    # When
    get(:subsystem_codes, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)

    subsystem_codes = response_as_json["data"]

    assert_equal(2, subsystem_codes.size)
    assert_equal("subsystem_as_not_server_client", subsystem_codes[0])
    assert_equal("subsystem_as_server_client", subsystem_codes[1])
  end

  private

  def change_member_name(member_code, new_name)
    params = {
      'memberClass' => "riigiasutus",
      'memberCode' => member_code,
      'memberName' => new_name
    }

    post(:member_edit, params)

    assert_response(:success)
  end
end
