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

# Encapsulates data necessary to get list of entries (for example security
# servers, requests or members) from the database.
class ListQueryParams
  attr_reader :sort_column, #database column name
      :sort_direction, # either 'asc' or 'desc
      :display_start, # number of entries to skip
      :display_length, #number of entries to return
      :search_string # string to search, blank if not provided

  def initialize(sort_column, sort_direction, display_start, display_length,
      search_string = "")
    if !["asc", "desc"].include?(sort_direction)
      raise "Sort direction must be either 'asc' or 'desc', \
          but is '#{sort_direction}'"
    end

    display_length_as_int = display_length.to_i

    if display_length_as_int < 0
      raise "Display length must be non-negative integer!"
    end

    @sort_column = sort_column
    @sort_direction = sort_direction
    @display_start = display_start.to_i
    @display_length = display_length_as_int
    @search_string = search_string != nil ? search_string.downcase : ""
  end

  def to_s
    "ListQueryParams:
       Sort column: '#@sort_column'
       Sort direction: '#@sort_direction'
       Display start: '#@display_start'
       Display length: '#@display_length'
       Search string: '#@search_string'
    "
  end
end
