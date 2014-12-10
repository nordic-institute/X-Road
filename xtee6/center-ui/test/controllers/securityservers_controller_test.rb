require 'test_helper'

class SecurityserversControllerTest < ActionController::TestCase
  test "Should perform full advanced search" do
    # Given
    params = {:advancedSearchParams =>
      {
        :name => "This member should NOT belong to group 'vallavalitsused'",
        :memberClass => "riigiasutus",
        :memberCode => "member_out_of_vallavalitsused",
        :serverCode => "extraSecureServer"
      }.to_json()
    }

    # When
    get(:securityservers_advanced_search, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)
    actual_servers = response_as_json["data"]

    assert_equal(1, actual_servers.size)
    assert_equal("extraSecureServer", actual_servers[0]["server_code"])
  end

  test "Should get management requests for server" do
    # Given
    params = {
      'ownerClass' => "riigiasutus",
      'ownerCode' => "member_out_of_vallavalitsused",
      'serverCode' => "tuumaserver",
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
