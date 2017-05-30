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
