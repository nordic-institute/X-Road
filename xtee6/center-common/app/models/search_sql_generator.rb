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