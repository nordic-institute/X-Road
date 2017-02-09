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

class SearchSqlGeneratorTest < ActiveSupport::TestCase

  # -- Simple search tests - start ---

  test "Raise error if 'columns' for simple search is not array" do
    error = assert_raises(RuntimeError) do
      SimpleSearchSqlGenerator.new("noArray", "123")
    end

    assert_equal("Columns must be given as an array", error.message)
  end

  test "Raise error if 'searchable' for simple search is not a string" do
    error = assert_raises(RuntimeError) do
      SimpleSearchSqlGenerator.new([], {})
    end

    assert_equal("Searchable must be given as string", error.message)
  end

  test "Simple search SQL with one column" do
    # Given/when
    sql_generator =
        SimpleSearchSqlGenerator.new(['identifiers.member_class'], "123")

    # Then
    params = sql_generator.params

    assert_equal(" lower(identifiers.member_class) LIKE ?", sql_generator.sql)
    assert_equal(1, params.length)
    assert_equal("%123%", params[0])
  end

  test "Simple search with two SQL columns" do
    # Given
    columns = ["identifiers.member_class", "identifiers.member_code"]

    # When
    sql_generator = SimpleSearchSqlGenerator.new(columns, "123")

    # Then
    params = sql_generator.params

    assert_equal(" lower(identifiers.member_class) LIKE ? "\
        "OR lower(identifiers.member_code) LIKE ?",
        sql_generator.sql)

    assert_equal(2, params.length)
    assert_equal("%123%", params[0])
    assert_equal("%123%", params[1])
  end

  # -- Simple search tests - end ---

  # -- Advanced search tests - start ---

  test "Raise error when 'columns_and_values' for advanced search is not hash" do
    error = assert_raises(RuntimeError) do
      AdvancedSearchSqlGenerator.new([])
    end

    assert_equal("Parameters must be given as hash", error.message)
  end

  test "Advanced search with one SQL column" do
    # Given
    columns_and_values = {'identifiers.member_code' => "123"}

    # When
    sql_generator = AdvancedSearchSqlGenerator.new(columns_and_values)

    # Then
    params = sql_generator.params

    assert_equal(" lower(identifiers.member_code) LIKE ?", sql_generator.sql)
    assert_equal(1, params.length)
    assert_equal("%123%", params[0])
  end

  test "Advanced search with two SQL columns" do
    # Given
    columns_and_values = {
        "identifiers.member_class" => "class",
        "identifiers.member_code" => "code"
    }

    # When
    sql_generator = AdvancedSearchSqlGenerator.new(columns_and_values)

    # Then
    params = sql_generator.params

    assert_equal(" lower(identifiers.member_class) LIKE ? "\
        "AND lower(identifiers.member_code) LIKE ?",
        sql_generator.sql)

    assert_equal(2, params.length)
    assert_equal("%class%", params[0])
    assert_equal("%code%", params[1])
  end

  test "Advanced search with no column value" do
    # Given
    columns_and_values = {
        "identifiers.member_class" => "class",
        "identifiers.member_code" => nil
    }

    # When
    sql_generator = AdvancedSearchSqlGenerator.new(columns_and_values)

    # Then
    params = sql_generator.params

    assert_equal(" lower(identifiers.member_class) LIKE ?", sql_generator.sql)

    assert_equal(1, params.length)
    assert_equal("%class%", params[0])
  end
  # -- Advanced search tests - end ---
end