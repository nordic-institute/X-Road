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

class OptionalConfPartsTest < ActiveSupport::TestCase

  def setup
    @result_file = "build/optional_content"

    FileUtils.rm_f(@result_file)
  end

  # Script test/resources/validate_conf_part_SUCCESS.sh goes together
  # with this test.
  test "Should validate optional conf parts successfully" do
    # Given
    validation_program = "test/resources/validate_conf_part_SUCCESS.sh"
    file_bytes = "SUCCESSFULLY VALIDATED"
    content_identifier = "IDENTIFIERMAPPING"

    validator = OptionalConfParts::Validator.new(
        validation_program, file_bytes, content_identifier)

    # When
    actual_stderr = validator.validate()

    # Then
    expected_stderr = ["firstWarningLine", "secondWarningLine"]
    assert_equal(expected_stderr, actual_stderr)

    actual_validated_bytes = get_validated_bytes()
    assert_equal(file_bytes, actual_validated_bytes)
  end

  # Script test/resources/validate_conf_part_LOT_OF_STDOUT.sh goes together
  # with this test.
  test "Should validate optional conf parts successfully with large stdout" do
    # Given
    validation_program = "test/resources/validate_conf_part_LOT_OF_STDOUT.sh"
    file_bytes = get_large_file_bytes()
    content_identifier = "IDENTIFIERMAPPING"

    validator = OptionalConfParts::Validator.new(
        validation_program, file_bytes, content_identifier)

    # When
    actual_stderr = validator.validate()

    # Then
    expected_stderr = ["Before large stdout", "After large stdout"]
    assert_equal(expected_stderr, actual_stderr)

    assert(file_bytes.start_with?("1234567890"))
  end

  # Script test/resources/validate_conf_part_FAILURE.sh goes together
  # with this test.
  test "Should raise Exception when script exit status not zero" do
    # Given
    validation_program = "test/resources/validate_conf_part_FAILURE.sh"
    file_bytes = "VALIDATION FAILED"
    content_identifier = "CLASSIFIERS"

    validator = OptionalConfParts::Validator.new(
        validation_program, file_bytes, content_identifier)

    # When/then
    e = assert_raise(OptionalConfParts::ValidationException) do
      validator.validate()
    end

    expected_stderr = ["firstErrorLine"]

    assert_equal(expected_stderr, e.stderr)
  end

  test "Should raise Exception when validation script does not exist" do
    # Given
    validation_program = "test/resources/validate_conf_part_NONEXISTENT.sh"
    file_bytes = "VALIDATION FAILED"
    content_identifier = "CLASSIFIERS"

    validator = OptionalConfParts::Validator.new(
        validation_program, file_bytes, content_identifier)

    # When/then
    e = assert_raise(OptionalConfParts::ValidationException) do
      validator.validate()
    end

    assert_equal([], e.stderr)
  end

  def get_large_file_bytes
    result = ""

    (1..1000000).each do
      result << "1234567890\n"
    end

    return result
  end

  def get_validated_bytes()
    raw_content = IO.read(@result_file)

    return raw_content.strip!
  end

  def get_large_file_bytes
    result = ""

    (1..1000000).each do
      result << "1234567890"
    end

    return result
  end
end
