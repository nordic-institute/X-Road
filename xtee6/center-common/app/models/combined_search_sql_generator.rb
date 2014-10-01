# Uses combination of simple search AND advanced search.
class CombinedSearchSqlGenerator < SearchSqlGenerator
  def initialize(advanced_search_columns_and_values, simple_search_string)
    columns = advanced_search_columns_and_values.keys

    @advanced_search_generator =
        AdvancedSearchSqlGenerator.new(advanced_search_columns_and_values)
    @simple_search_generator =
        SimpleSearchSqlGenerator.new(columns, simple_search_string)

    generate()
  end

  def generate
    @sql = ""
    @params = []

    advanced_sql = @advanced_search_generator.sql
    simple_sql = @simple_search_generator.sql

    if advanced_sql.empty?
      @sql = simple_sql
    elsif simple_sql.empty?
      @sql = advanced_sql
    else
      @sql = "(#{advanced_sql}) AND (#{simple_sql})"
    end

    @params += @advanced_search_generator.params
    @params += @simple_search_generator.params
  end
end
