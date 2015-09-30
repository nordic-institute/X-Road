# Generates SQL for advanced search, takes hash with columns and values.
class AdvancedSearchSqlGenerator < SearchSqlGenerator
  def initialize(columns_and_values)
    raise "Parameters must be given as hash" if !columns_and_values.is_a?(Hash)
    @columns_and_values = columns_and_values

    generate()
  end

  private

  def generate
    @sql = ""
    @params = []

    @columns_and_values.each_pair do |key, value|
      if value
        @sql << " AND" unless @sql.empty?

        @sql << get_column_statement(key)
        @params << "%#{value}%"
      end
    end
  end
end