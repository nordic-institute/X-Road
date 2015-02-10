require 'test_helper'
require 'common-ui/uploaded_file.rb'

# Actually tests functionality of common-ui.
class UploadedFileValidatorTest < ActiveSupport::TestCase
  class MockContentValidator
    def validate(file_path, original_filename)
      must_include = "Valid content"
      content = IO.read(file_path)

      unless content.include?(must_include)
        raise "File content must include string '#{must_include}', "\
           "but does not."
      end
    end
  end

  def setup
    @validation = Proc.new do |uploaded_file|
      restrictions = CommonUi::UploadedFile::Restrictions.new(
          ["txt"], ["text/plain"]);
      content_validator = MockContentValidator.new

      CommonUi::UploadedFile::Validator.new(
          uploaded_file, content_validator, restrictions).validate
    end
  end

  test "Should pass file validation" do
    assert_uploaded_file(
        "uploaded_file_valid.txt", "text/plain") do |file|
      @validation.call(file)
    end
  end

  test "Should raise error when content invalid" do
    assert_upload_failure(
        "uploaded_file_invalid.txt", "text/plain") do |file|
      @validation.call(file)
    end
  end

  test "Should raise error when extension invalid" do
    assert_upload_failure(
        "uploaded_file_invalid_extension.ini", "text/plain") do |file|
      @validation.call(file)
    end
  end

  test "Should raise error when content type invalid" do
    assert_upload_failure(
        "uploaded_file_valid.txt", "text/xml") do |file|
      @validation.call(file)
    end
  end

  test "Should validate with no restrictions" do
    @validation = Proc.new do |uploaded_file|
      content_validator = MockContentValidator.new

      CommonUi::UploadedFile::Validator.new(
          uploaded_file, content_validator).validate
    end

    assert_uploaded_file(
        "uploaded_file_valid.txt", "text/plain") do |file|
      @validation.call(file)
    end
  end
end
