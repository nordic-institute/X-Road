require 'test_helper'

class GzipFileValidatorTest < ActiveSupport::TestCase
  def setup
    @validation = Proc.new do |uploaded_file|
      GzipFileValidator.new(uploaded_file).validate()
    end
  end

  test "Should raise error when wrong file extension" do
    assert_upload_failure("import_wrong_extension.txt") do |file|
      @validation.call(file)
    end
  end

  test "Should raise error when content type invalid" do
    assert_upload_failure("import_fake.tgz", "text/plain") do |file|
      @validation.call(file)
    end
  end

  test "Should raise error when content invalid" do
    assert_upload_failure(
        "import_fake.tgz", "application/x-compressed-tar") do |file|
      @validation.call(file)
    end
  end

  test "Should pass validation with real tgz" do
    assert_uploaded_file(
        "xtee_cs_db.tgz", "application/x-compressed-tar") do |file|
      @validation.call(file)
    end
  end
end
