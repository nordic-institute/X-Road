# Generates SQL fragment for searching list items from the database.
#
# The result could be used like this:
#
# RELATION.where(generator.sql, *generator.params)
class SearchSqlGenerator
  attr_reader :sql, :params

  private

  def get_column_statement(column)
    " lower(#{column}) LIKE ?"
  end
end