require 'test_helper'

class GzipFileValidatorTest < ActiveSupport::TestCase
  def setup
    @validator = GzipFile::Validator.new()
  end

  test "Should raise error when content invalid" do
    error = assert_raises(RuntimeError) do
      original_filename = "import_fake.tgz"
      @validator.validate(
          get_file_path(original_filename),
          original_filename)
    end

    assert(error.message.include?("gzip"),
        "Error message should include word 'gzip', but actual is:\n"\
        "#{error.message}")
  end

  test "Should pass validation with real tgz" do
    original_filename = "xtee_cs_db.tgz"
    @validator.validate(
        get_file_path(original_filename),
        original_filename)
  end
end
