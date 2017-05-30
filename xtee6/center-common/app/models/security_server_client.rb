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

class SecurityServerClient < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  has_and_belongs_to_many :security_servers, :join_table => "server_clients"
  belongs_to :server_client,
    :class_name => "ClientId",
    :autosave => true,
    :dependent => :destroy

  after_create {|record| save_identifier(record)}

  after_update {|record| update_name_mapping(record)}

  before_destroy { |record|
    register_client_deletion_requests(record)
    clear_name_mapping(record)
    record.security_servers.clear
    remove_client_from_global_groups(record)
  }

  # Finds a XROAD member or subsystem by ClientId
  # Returns nil, if not found.
  def self.find_by_id(client_id)
    Rails.logger.info("SecurityServerClient.find_by_id(#{client_id})")

    if client_id.subsystem_code == nil
      # Find XROAD member
      return XroadMember.find_by_code(client_id.member_class, client_id.member_code)
    else
      # Find subsystem
      return Subsystem.find_by_code(
          client_id.member_class,
          client_id.member_code,
          client_id.subsystem_code)
    end
  end

  # Returns all members and subsystems according to query parameters
  def self.get_clients(query_params, advanced_search_params = nil)
    logger.info("SecurityServerClient.get_clients(#{query_params})")

    identifiers = get_clients_relation(query_params, advanced_search_params)

    return get_clients_with_name(identifiers)
  end

  # 'searchable' can be of type either 'AdvancedSearchParams' or 'String'.
  # 'secondary_searchable', if present, can be only of type 'String'.
  def self.get_clients_count(searchable = "", secondary_searchable = nil)
    get_search_relation(searchable, secondary_searchable).count
  end

  # Returns all members and subsystems that do not yet belong to particular
  # global group.
  def self.get_remaining_clients_for_group(group_id, query_params,
      advanced_search_params = nil)
    logger.info("SecurityServerClient.get_remaining_clients_for_group(\
        '#{group_id}', '#{query_params}', #{advanced_search_params})")

    identifiers = get_clients_relation(query_params, advanced_search_params).
      where(get_excluded_group_members_relation(group_id))

    return get_clients_with_name(identifiers)
  end

  def self.get_remaining_clients_count(group_id, searchable = "")
    get_search_relation(searchable).
        where(get_excluded_group_members_relation(group_id)).count
  end

  # Returns all server clients except for subsystems that are already
  # clients for the server.
  def self.get_addable_clients_for_server(
      server_code, query_params, advanced_search_params = nil)
    logger.info("SecurityServerClient.get_addable_clients_for_server(\
        '#{server_code}', '#{query_params}', '#{advanced_search_params}')")

    identifiers = get_clients_relation(query_params, advanced_search_params).
        where(get_excluded_ids_relation(server_code ?
            get_excluded_server_client_ids(server_code) : []))

    return get_clients_with_name(identifiers)
  end

  def self.get_addable_clients_count(
      server_code, searchable = "", advanced_search_params = nil)
    return get_search_relation(searchable).
        where(get_excluded_ids_relation(server_code ?
            get_excluded_server_client_ids(server_code) : [])).count
  end

  private

  # Static private methods: start

  def self.get_clients_with_name(identifiers)
    result = []

    identifiers.each do |each|
      result << {
        :name => XroadMember.get_name(each.member_class, each.member_code),
        :identifier => each
      }
    end

    return result
  end

  # -- Relation helpers - start ---

  def self.get_excluded_group_member_ids(group_id)
    group_members = GlobalGroupMember.where(:global_group_id => group_id)
    excluded_client_ids = []
    group_members.each do |each_member|
      group_member_id = each_member.group_member

      all_member_ids = ClientId.where({
          :xroad_instance => group_member_id.xroad_instance,
          :member_class => group_member_id.member_class,
          :member_code => group_member_id.member_code,
          :subsystem_code => group_member_id.subsystem_code
      })

      all_member_ids.each do |each_member_id|
        excluded_client_ids << each_member_id.id
      end
    end

    excluded_client_ids
  end

  def self.get_excluded_server_client_ids(server_code)
    security_server = SecurityServer.where(:server_code => server_code).first()

    if security_server == nil
      raise "Security server with server code '#{server_code}' not found."
    end

    result = []
    security_server.security_server_clients.each do |each|
      if each.is_a?(Subsystem)
        result << each.server_client.id
      end
    end

    return result
  end

  def self.get_excluded_group_members_relation(group_id)
    excluded_group_member_ids = get_excluded_group_member_ids(group_id)

    return get_excluded_ids_relation(excluded_group_member_ids)
  end

  def self.get_excluded_ids_relation(excluded_ids)
    identifiers_table = _table = Arel::Table.new(:identifiers)

    return excluded_ids.empty? ? "":
        identifiers_table[:id].not_in(excluded_ids)
  end

  def self.get_clients_relation(query_params, advanced_search_params = nil)
    return get_search_relation(
            advanced_search_params, query_params.search_string).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  # 'searchable' can be of type either 'AdvancedSearchParams' or 'String'.
  # 'secondary_searchable', if present, can be only of type 'String'.
  def self.get_search_relation(searchable, secondary_searchable = nil)
    sql_generator = get_sql_generator(searchable, secondary_searchable)

    logger.debug("SQL generator: #{sql_generator.class}, sql: "\
        "'#{sql_generator.sql}', params: '#{sql_generator.params}'")

    Identifier.
        where(sql_generator.sql, *sql_generator.params).
        joins(get_client_id_and_client_name_join_sql).
        joins(get_client_and_client_id_join_sql)
  end

  # 'searchable' can be of type either 'AdvancedSearchParams' or 'String'.
  # 'secondary_searchable', if present, can be only of type 'String'.
  def self.get_sql_generator(searchable, secondary_searchable = nil)
    if searchable.is_a?(AdvancedSearchParams)
      if secondary_searchable.blank?
        return AdvancedSearchSqlGenerator.new(
            map_advanced_search_params(searchable))
      else
        return CombinedSearchSqlGenerator.new(
            map_advanced_search_params(searchable),
            secondary_searchable)
      end
    end

    search_string = searchable == nil ? secondary_searchable : searchable

    return SimpleSearchSqlGenerator.new(get_searchable_columns, search_string)
  end

  def self.get_client_id_and_client_name_join_sql
    "INNER JOIN security_server_client_names
      ON security_server_client_names.client_identifier_id = identifiers.id"
  end

  def self.get_client_and_client_id_join_sql
    "INNER JOIN security_server_clients
      ON security_server_clients.server_client_id = identifiers.id"
  end

  def self.map_advanced_search_params(searchable)
    {
        "security_server_client_names.name" => searchable.name,
        "identifiers.member_code" => searchable.member_code,
        "identifiers.member_class" => searchable.member_class,
        "identifiers.subsystem_code" => searchable.subsystem_code,
        "identifiers.xroad_instance" => searchable.xroad_instance,
        "identifiers.object_type" => searchable.object_type
    }
  end

  def self.get_searchable_columns
    [
        "security_server_client_names.name",
        "identifiers.member_code",
        "identifiers.member_class",
        "identifiers.subsystem_code",
        "identifiers.xroad_instance",
        "identifiers.object_type"
    ]
  end

  # -- Relation helpers - end ---

  # Static private methods: end

  def save_identifier(record)
    raise "This method must be implemented by subclass!"
  end

  def update_name_mapping(record)
    raise "This method must be implemented by subclass!"
  end

  def clear_name_mapping(record)
    SecurityServerClientName.
        where(:client_identifier_id => record.server_client_id).delete_all
  end

  def remove_client_from_global_groups(record)
    GlobalGroup.all.each do |each|
      each.remove_member(record.server_client)
    end
  end

  def register_client_deletion_requests(client)
    server_client_id = client.server_client

    client.security_servers.each do |each|
      comment = "'#{client.server_client.to_s}' deletion"

      request = ClientDeletionRequest.new(
          :security_server => each.get_server_id(),
          :sec_serv_user => server_client_id.clean_copy(),
          :comments => comment,
          :server_user_name => client.name,
          :origin => Request::CENTER)
      request.register()

      logger.debug("Successfully registered client deletion request"\
        " '#{request.inspect}'")
    end
  end
end
