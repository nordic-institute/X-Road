require 'test_helper'

class AuthCertValidatorTest < ActiveSupport::TestCase
  def setup
    @validator = AuthCertValidator.new
  end

  test "Should raise error when signing cert uploaded" do
    assert_raises(RuntimeError) do
      @validator.validate(get_file_path("cert_sign.pem"), "")
    end
  end

  test "Should pass validation for auth cert" do
    @validator.validate(get_file_path("cert_auth.pem"), "")
  end
end
