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
