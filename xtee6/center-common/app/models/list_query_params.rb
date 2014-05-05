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

    @sort_column = sort_column
    @sort_direction = sort_direction
    @display_start = display_start
    @display_length = display_length
    @search_string = search_string.downcase
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