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
