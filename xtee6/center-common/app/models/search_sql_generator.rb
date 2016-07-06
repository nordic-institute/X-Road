# Generates SQL fragment for searching list items from the database.
#
# The result could be used like this:
#
# RELATION.where(generator.sql, *generator.params)
class SearchSqlGenerator
  TIMESTAMP_COLUMNS = [
      "global_groups.updated_at",

      "ca_infos.valid_from",
      "ca_infos.valid_to",

      "approved_tsas.valid_from",
      "approved_tsas.valid_to"
  ]

  attr_reader :sql, :params

  private

  def get_column_statement(column)
    if TIMESTAMP_COLUMNS.include?(column)
      return " #{CommonSql.turn_timestamp_into_text(column)} LIKE ?"
    end

    return " lower(#{column}) LIKE ?"
  end
end

