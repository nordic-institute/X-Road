require 'test_helper'

class AuthCertValidatorTest < ActiveSupport::TestCase
  def setup
    @validation = Proc.new do |uploaded_file|
      AuthCertValidator.new(uploaded_file).validate()
    end
  end

  test "Should raise error when signing cert uploaded" do
    assert_upload_failure("cert_sign.pem") do |file|
      @validation.call(file)
    end
  end

  test "Should pass validation for auth cert" do
    assert_uploaded_file("cert_auth.pem") do |file|
      @validation.call(file)
    end
  end
end
