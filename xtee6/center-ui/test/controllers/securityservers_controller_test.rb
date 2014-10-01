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
end
