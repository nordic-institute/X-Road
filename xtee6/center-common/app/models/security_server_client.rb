class SecurityServerClient < ActiveRecord::Base

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

  # Finds a SDSB member or subsystem by ClientId
  # Returns nil, if not found.
  def self.find_by_id(client_id)
    puts "SecurityServerClient.find_by_id(#{client_id})"
    if client_id.subsystem_code == nil
      # Find SDSB member
      SdsbMember.find_by_code(client_id.member_class, client_id.member_code)
    else
      # Find subsystem
      Subsystem.find_by_code(
          client_id.member_class,
          client_id.member_code,
          client_id.subsystem_code)
    end
  end

  # Returns all members and subsystems according to query parameters
  def self.get_clients(query_params, advanced_search_params = nil)
    logger.info("SecurityServerClient.get_clients(#{query_params})")

    identifiers = get_clients_relation(query_params, advanced_search_params)

    get_clients_with_name(identifiers)
  end

  def self.get_clients_count(searchable = "")
    get_search_relation(searchable).count
  end

  # Returns all members and subsystems that do not yet belong to particular
  # global group.
  def self.get_remaining_clients_for_group(group_id, query_params,
      advanced_search_params = nil)
    logger.info("SecurityServerClient.get_remaining_clients_for_group(\
    '#{group_id}', '#{query_params}', #{advanced_search_params})")

    identifiers = get_clients_relation(query_params, advanced_search_params).
      where(get_excluded_clients_relation(group_id))

    get_clients_with_name(identifiers)
  end

  def self.get_remaining_clients_count(group_id, searchable = "")
    get_search_relation(searchable).
        where(get_excluded_clients_relation(group_id)).count
  end

  private

  # Static private methods: start

  def self.get_clients_with_name(identifiers)
    result = []

    identifiers.each do |each|
      member = SdsbMember.find_by_code(each.member_class, each.member_code)
      result << {
        :name => member.name,
        :identifier => each
      }
    end

    result
  end

  # -- Relation helpers - start ---

  def self.get_excluded_client_ids(group_id)
    group_members = GlobalGroupMember.where(:global_group_id => group_id)
    excluded_client_ids = []
    group_members.each do |each_member|
      group_member_id = each_member.group_member

      all_member_ids = ClientId.where({
          :sdsb_instance => group_member_id.sdsb_instance,
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

  def self.get_excluded_clients_relation(group_id)
    identifiers_table = _table = Arel::Table.new(:identifiers)
    excluded_client_ids = get_excluded_client_ids(group_id)

    result = excluded_client_ids.empty?  ? "":
        identifiers_table[:id].not_in(excluded_client_ids)

    result
  end

  def self.get_clients_relation(query_params, advanced_search_params = nil)
    searchable = advanced_search_params ? advanced_search_params :
        query_params.search_string

    get_search_relation(searchable).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_search_relation(searchable)
    sql_generator = searchable.is_a?(AdvancedSearchParams) ?
        AdvancedSearchSqlGenerator.new(map_advanced_search_params(searchable)):
        SimpleSearchSqlGenerator.new(get_searchable_columns, searchable)

    Identifier.
        where(sql_generator.sql, *sql_generator.params).
        joins(get_client_id_and_client_name_join_sql)
  end

  def self.get_client_id_and_client_name_join_sql
    "INNER JOIN security_server_client_names
      ON security_server_client_names.client_identifier_id = identifiers.id"
  end

  def self.map_advanced_search_params(searchable)
    {
        "security_server_client_names.name" => searchable.name,
        "identifiers.member_code" => searchable.member_code,
        "identifiers.member_class" => searchable.member_class,
        "identifiers.subsystem_code" => searchable.subsystem_code,
        "identifiers.sdsb_instance" => searchable.sdsb_instance,
        "identifiers.object_type" => searchable.object_type
    }
  end

  def self.get_searchable_columns
    [
        "security_server_client_names.name",
        "identifiers.member_code",
        "identifiers.member_class",
        "identifiers.subsystem_code",
        "identifiers.sdsb_instance",
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
          :origin => Request::CENTER)
      request.register()

      logger.debug("Successfully registered client deletion request"\
        " '#{request.inspect}'")
    end
  end
end
