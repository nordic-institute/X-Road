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
end
