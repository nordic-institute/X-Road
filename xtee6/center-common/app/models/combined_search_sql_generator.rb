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
