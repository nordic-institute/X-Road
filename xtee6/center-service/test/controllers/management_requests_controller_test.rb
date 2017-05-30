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

require 'common-ui/io_utils'

class ManagementRequestsControllerTest < ActionController::TestCase

  def setup
    server_id =
        SecurityServerId.from_parts("EE", "GOV", "codeOfMember", "serverCode")
    client_id =
        ClientId.from_parts("EE", "GOV", "codeOfMember", "codeOfSubsystem")

    client_reg_request = ClientRegRequest.new(
         :security_server => server_id,
         :sec_serv_user => client_id,
         :origin => Request::SECURITY_SERVER)

    client_reg_request.register()
  end

  # TODO Test that registration request is received and names are updated.

  # Case related to RM issue #4408
  test "Should refuse to accept management request of different instance" do
    # Given
    @request.env["CONTENT_TYPE"] = "text/xml"
    @request.env["RAW_POST_DATA"] = read_request_message()

    # When
    post(:create)

    # Then
    assert_response(:success)

    response_content = response.body
    puts "Response received:\n#{response_content}"

    assert_response_content(response_content)
  end

  # For some reason parser wants message inlined.
  def read_request_message
    raw_request = CommonUi::IOUtils.read_to_array(
        "#{ENV["XROAD_HOME"]}/center-service/test/resources/"\
        "client_reg_request_CONTROLLERTEST_WRONG_INSTANCE.soap")

    raw_request.map!() { |line| line.strip() }

    return raw_request.join("")
  end

  def assert_response_content(content)
    # Response must contain this one:
    faultstring =
        "Invalid management service address. "\
        "Contact central server administrator."

    assert(
        content.include?(faultstring),
        "Content does not include expected faultstring.")
  end
end
