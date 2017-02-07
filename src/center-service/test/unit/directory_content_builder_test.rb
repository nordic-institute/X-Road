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

# Mock class, related to fixtures in the database.
class HashCalculator
  @@allowed_file_contents = [
      "<XMLContainingPrivateParameters>",
      "<XMLContainingSharedParameters>"]

  def calculateFromString(data)
    if !@@allowed_file_contents.include?(data)
      raise "Not valid content for mock hash calculator: '#{data}'"
    end

    return "Calculated hash for data '#{data}'"
  end
end

class DirectoryContentBuilderTest < ActiveSupport::TestCase
  test "Should build directory content" do
    # Given
    data_boundary = "dataBoundary"
    expire_date = Time.now()
    hash_calculator = HashCalculator.new("http://www.w3.org/2000/09/xmldsig#sha1")

    content_builder = DirectoryContentBuilder.new(
        expire_date, hash_calculator,"20141129155645384197868")

    # When
    content = content_builder.build(data_boundary)
    puts "Built directory content:\n#{content}"

    # Then - let us verify presence of just one line.
    check_line = "Content-identifier: PRIVATE-PARAMETERS; instance='EE'"
    assert(
        content.include?(check_line),
        "Content does not include line '#{check_line}'")
  end

  test "Should limit directory content when whitelist included" do
    # Given
    data_boundary = "dataBoundary"
    expire_date = Time.now()
    hash_calculator = HashCalculator.new("http://www.w3.org/2000/09/xmldsig#sha1")

    content_builder = DirectoryContentBuilder.new(
        expire_date,
        hash_calculator,
        "20141129155645384197868",
        ["SHARED-PARAMETERS"])

    # When
    content = content_builder.build(data_boundary)
    puts "Built directory content:\n#{content}"

    # Then - private parameters must be excluded
    assert(!content.include?("PRIVATE-PARAMETERS"),
        "Private parameters must not be included")
  end
end

