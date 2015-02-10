require 'test_helper'
require 'common-ui/tar_file.rb'

class TarFileValidatorTest < ActiveSupport::TestCase
  def setup
    @validator = CommonUi::TarFile::Validator.new()
  end

  test "Should raise error when content invalid" do
    error = assert_raises(RuntimeError) do
      original_filename = "backup_fake.tar"
      file = get_file(original_filename)

      @validator.validate(file, original_filename)
    end

    assert(error.message.include?("tar"))
  end

  test "Should pass validation with real tgz" do
      original_filename = "backup_real.tar"
    file = get_file(original_filename)

    @validator.validate(file, original_filename)
  end
end
