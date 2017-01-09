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

class CombinedSearchSqlGeneratorTest < ActiveSupport::TestCase

  test "Should combine correct SQL" do
    # Given
    advanced_search_columns_and_values = {
      'table.col1' => 'valAdvanced1',
      'table.col2' => 'valAdvanced2',
    }

    simple_search_string = "valSimple"

    # When
    generator = CombinedSearchSqlGenerator.new(
        advanced_search_columns_and_values, simple_search_string)

    # Then
    expected_sql = '( lower(table.col1) LIKE ? AND lower(table.col2) LIKE ?) '\
        'AND ( lower(table.col1) LIKE ? OR lower(table.col2) LIKE ?)'
    expected_params = [
        "%valAdvanced1%", "%valAdvanced2%", "%valsimple%", "%valsimple%"]

    assert_equal(expected_sql, generator.sql)
    assert_equal(expected_params, generator.params)
  end
end
