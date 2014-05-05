class SdsbMember < SecurityServerClient
  include Validators

  class DuplicateMemberValidator < ActiveModel::Validator
    def validate(new_record)
      member_code = new_record.member_code
      member_class_id = new_record.member_class_id

      duplicate = SdsbMember.where(
          :member_code => member_code,
          :member_class_id => member_class_id).first

      if (duplicate)
        member_class = MemberClass.find(member_class_id)
        raise I18n.t("validation.member_exists",
          :member_class => member_class.code, :member_code => member_code)
      end
    end
  end

  validates :name, :member_class, :member_code, :present => true
  validates_with DuplicateMemberValidator, :on => :create

  belongs_to :member_class, :inverse_of => :sdsb_members
  has_many :owned_servers, :class_name => "SecurityServer",\
    :dependent => :destroy
  has_many :subsystems, :dependent => :destroy

  # Finds a SDSB member by member class and code.
  # Returns nil, if not found.
  def self.find_by_code(member_class, member_code)
    members = SdsbMember.joins(:member_class).where(
        # WTF: why does not name of the relation (member_class) work?
        :member_classes => {:code => member_class},
        :member_code => member_code).readonly(false)
    if members.empty? then nil else members[0] end
  end

  def self.get_members(query_params)
    get_search_relation(query_params.search_string).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_member_count(searchable)
    searchable.empty? ?
        SdsbMember.count :
        get_search_relation(searchable).count
  end

  # Returns GlobalGroupMember records belonging to particular member.
  def self.get_global_group_members(member_class, member_code)
    GlobalGroupMember.joins(:group_member).where(
        :identifiers => {
             :member_class => member_class,
             :member_code => member_code
        }
    )
  end

  def self.get_remaining_global_groups(member_class, member_code)
    # TODO: Could we do this with single query?
    existing_members = get_global_group_members(member_class, member_code)
    result = GlobalGroup.find(:all)

    existing_members.each do |each|
      member_group = each.global_group
      result.delete(member_group) if result.include?(member_group)
    end

    result
  end

  private

  def self.get_search_relation(searchable)
    search_params = get_search_sql_params(searchable)

    SdsbMember.
        where(get_search_sql, *search_params).
        joins(:member_class)
  end

  def self.get_search_sql
    "lower(security_server_clients.name) LIKE ?
    OR lower(member_classes.code) LIKE ?
    OR lower(security_server_clients.member_code) LIKE ?"
  end

  def self.get_search_sql_params(searchable)
    ["%#{searchable}%", "%#{searchable}%", "%#{searchable}%"]
  end

  def save_identifier(record)
    identifier = ClientId.from_parts(
        SystemParameter.sdsb_instance,
        record.member_class.code,
        record.member_code)
    record.server_client = identifier
    record.save!

    SecurityServerClientName.create!(
        :name => record.name,
        :client_identifier_id => identifier.id)
  end

  def update_name_mapping(record)
    member_mapping = SecurityServerClientName.
        where(:client_identifier_id => record.server_client.id).first

    if member_mapping && record.changed?
      member_mapping.update_attributes!(:name => record.name)

      record.subsystems.each do |each|
        subsystem_mapping = SecurityServerClientName.
            where(:client_identifier_id => each.server_client.id).first
        subsystem_mapping.update_attributes!(:name => record.name)
      end
    end
  end
end
