require 'test_helper'

class SdsbMemberTest < ActiveSupport::TestCase

  test "Should load all requests" do
    # Given
    query_params = ListQueryParams.new(
        "requests.created_at", "desc", 0, 10)

    # When
    requests = SdsbMember.get_management_requests(
        "riigiasutus", "member_in_vallavalitsused", query_params)

    # Then
    assert_equal(2, requests.size)
  end

  test "Should find requests with offset" do
    # Given
    query_params = ListQueryParams.new(
        "requests.created_at", "desc", 1, 10)

    # When
    requests = SdsbMember.get_management_requests(
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
    requests = SdsbMember.get_management_requests(
        "riigiasutus", "member_in_vallavalitsused", query_params)

    # Then
    assert_equal(1, requests.size)

    request = requests[0]
    assert_equal("SECURITY_SERVER", request.origin)
    assert_equal("member_in_vallavalitsused", request.sec_serv_user.member_code)
  end
end
