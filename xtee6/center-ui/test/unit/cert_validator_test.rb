require 'test_helper'

class CertValidatorTest < ActiveSupport::TestCase

  test "Should not mix backup file and certificate up" do
    validator = CertValidator.new

    error = assert_raises(RuntimeError) do
      validator.validate(get_file_path("backup_real.tar"), "")
    end

    assert_equal(I18n.t("validation.invalid_cert"), error.message)
  end
end
