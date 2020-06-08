#
# The MIT License
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

class GlobalGroupMember < ActiveRecord::Base
  validates_presence_of :global_group_id
  belongs_to :global_group
  belongs_to :group_member,
    :class_name => "ClientId",
    :autosave => true,
    :dependent => :destroy

  def self.get_group_members(group_id, query_params,
      advanced_search_params = nil)
    logger.info("get_group_members(#{group_id}, '#{query_params}', \
        '#{advanced_search_params}'")

    searchable = advanced_search_params ? advanced_search_params :
        query_params.search_string

    get_search_relation(group_id, searchable).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_group_member_count(group_id, searchable)
    logger.info("get_group_member_count(#{group_id}, #{searchable})")

    get_search_relation(group_id, searchable).count
  end

  def self.remove_matching_members(group_id, searchable)
    logger.info("remove_matching_members(#{group_id}, #{searchable})")

    removed_members = get_search_relation(group_id, searchable).destroy_all
    GlobalGroup.find(group_id).update_member_count

    removed_members.map{ |m| m.group_member }.compact
  end

  private

  # FUTURE Consider denormalizing in order to make search more effective
  def self.get_search_relation(group_id, searchable)
    sql_generator = searchable.is_a?(AdvancedSearchParams) ?
        AdvancedSearchSqlGenerator.new(map_advanced_search_params(searchable)):
        SimpleSearchSqlGenerator.new(get_searchable_columns, searchable)

    get_all_members_relation(group_id).
        where(sql_generator.sql, *sql_generator.params).
        joins(:group_member).
        joins(get_identifier_to_member_join_sql)
  end

  def self.get_identifier_to_member_join_sql
    "LEFT JOIN security_server_clients
      ON identifiers.member_code = security_server_clients.member_code
    LEFT JOIN member_classes
      ON security_server_clients.member_class_id = member_classes.id
      AND identifiers.member_class = member_classes.code"
  end

  def self.get_all_members_relation(group_id)
    GlobalGroupMember.where(:global_group_id => group_id)
  end

  def self.map_advanced_search_params(searchable)
    return {
        "security_server_clients.name" => searchable.name,
        "identifiers.member_code" => searchable.member_code,
        "identifiers.member_class" => searchable.member_class,
        "identifiers.subsystem_code" => searchable.subsystem_code,
        "identifiers.xroad_instance" => searchable.xroad_instance,
        "identifiers.object_type" => searchable.object_type
    }
  end

  def self.get_searchable_columns
    return [   "security_server_clients.name",
        "identifiers.member_code",
        "identifiers.member_class",
        "identifiers.subsystem_code",
        "identifiers.xroad_instance",
        "identifiers.object_type",
        "CAST(identifiers.created_at AS TEXT)"
    ]
  end

end
