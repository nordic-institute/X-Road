class SimpleSearchSqlGenerator < SearchSqlGenerator
  def initialize(columns, searchable)
    raise "Columns must be given as an array" if !columns.is_a?(Array)
    raise "Searchable must be given as string" if !searchable.is_a?(String)

    @columns = columns
    @searchable = searchable
    generate()
  end

  private

  def generate
    @sql = ""
    @params = []

    @columns.each do |each|
      @sql << " OR" unless @sql.empty?
      @sql << get_column_statement(each)
      @params << "%#@searchable%"
    end
  end
end