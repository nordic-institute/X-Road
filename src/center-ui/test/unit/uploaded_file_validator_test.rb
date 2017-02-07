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
