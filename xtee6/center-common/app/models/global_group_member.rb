class GlobalGroupMember < ActiveRecord::Base
  include Validators

  validates :global_group_id, :present => true
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

  private

  def self.get_search_relation(group_id, searchable)
    sql_generator = searchable.is_a?(AdvancedSearchParams) ?
        AdvancedSearchSqlGenerator.new(map_advanced_search_params(searchable)):
        SimpleSearchSqlGenerator.new(get_searchable_columns, searchable)

    get_all_members_relation(group_id).
        where(sql_generator.sql, *sql_generator.params).
        joins(:group_member).
        joins(CommonSql::get_identifier_to_member_join_sql)
  end

  def self.get_all_members_relation(group_id)
    GlobalGroupMember.where(:global_group_id => group_id)
  end

  def self.map_advanced_search_params(searchable)
    {
        "security_server_clients.name" => searchable.name,
        "identifiers.member_code" => searchable.member_code,
        "identifiers.member_class" => searchable.member_class,
        "identifiers.subsystem_code" => searchable.subsystem_code,
        "identifiers.sdsb_instance" => searchable.sdsb_instance,
        "identifiers.object_type" => searchable.object_type
    }
  end

  def self.get_searchable_columns
    [   "security_server_clients.name",
        "identifiers.member_code",
        "identifiers.member_class",
        "identifiers.subsystem_code",
        "identifiers.sdsb_instance",
        "identifiers.object_type",
        "CAST(identifiers.created_at AS TEXT)"
    ]
  end

end
