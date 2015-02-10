require 'test_helper'

class SecurityserversControllerTest < ActionController::TestCase
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
