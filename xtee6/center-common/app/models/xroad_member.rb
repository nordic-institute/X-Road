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

class XroadMember < SecurityServerClient

  class DuplicateMemberValidator < ActiveModel::Validator
    def validate(new_record)
      member_code = new_record.member_code
      member_class_id = new_record.member_class_id

      duplicate = XroadMember.where(
          :member_code => member_code,
          :member_class_id => member_class_id).first

      if (duplicate)
        member_class = MemberClass.find(member_class_id)
        raise I18n.t("validation.member_exists",
          :member_class => member_class.code, :member_code => member_code)
      end
    end
  end

  after_update do |record|
    Request.update_names(
        record.member_class.code, record.member_code, record.name)
  end

  validates :name, :member_class, :member_code, :presence => true
  validates_with DuplicateMemberValidator, :on => :create

  belongs_to :member_class, :inverse_of => :xroad_members
  has_many :owned_servers, :class_name => "SecurityServer",\
    :dependent => :destroy, :foreign_key => "owner_id"
  has_many :subsystems, :dependent => :destroy

  # Returns subsystem codes of member in alphabetical order.
  def subsystem_codes()
    return self.subsystems.
        order(:subsystem_code).
        map(&:subsystem_code)
  end

  # Finds a XROAD member by member class and code.
  # Returns nil, if not found.
  def self.find_by_code(member_class, member_code)
    return get_by_code_relation(member_class, member_code).readonly(false).first
  end

  def self.get_name(member_class, member_code)
    return get_by_code_relation(member_class, member_code).map(&:name).first
  end

  def self.get_members(query_params)
    return get_search_relation(query_params.search_string).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_member_count(searchable = "")
    return get_search_relation(searchable).count
  end

  # Returns GlobalGroupMember records belonging to particular member.
  def self.get_global_group_members(
      member_class, member_code, subsystem_code = nil)
    query_params = {
        :member_class => member_class,
        :member_code => member_code
    }

    query_params[:subsystem_code] = subsystem_code if !subsystem_code.blank?

    GlobalGroupMember.joins(:group_member).where(
        :identifiers => query_params
    )
  end

  def self.get_used_servers(member_id)
    raw_server_clients = get_used_servers_relation(member_id).
        order("security_server_clients.subsystem_code asc")
    return get_used_servers_as_json(raw_server_clients)
  end

  def self.get_used_servers_count(member_id)
    return get_used_servers_relation(member_id).count
  end

  def self.get_remaining_global_groups(
      member_class, member_code, subsystem_code)
    logger.debug("get_remaining_global_groups(\
      '#{member_class}', '#{member_code}', '#{subsystem_code}')");

    existing_members = get_global_group_members(
        member_class, member_code, subsystem_code)

    result = GlobalGroup.find(:all)

    is_xroad_member = subsystem_code.blank?

    existing_members.each do |each|
      member_group = each.global_group

      next if !result.include?(member_group)

      member_type = each.group_member.object_type
      next if member_type.eql?("SUBSYSTEM") && is_xroad_member

      result.delete(member_group)
    end

    result.delete(GlobalGroup.security_server_owners_group)

    logger.debug("Remaining global groups: '#{result}'")
    return result
  end

  def self.get_management_requests(member_class, member_code, query_params)
    request_ids = get_management_request_ids(member_class, member_code)

    return get_management_requests_relation(member_class, member_code).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_management_requests_count(member_class, member_code)
    get_management_requests_relation(member_class, member_code).count()
  end

  private

  def self.get_search_relation(searchable)
    sql_generator =
            SimpleSearchSqlGenerator.new(get_searchable_columns(), searchable)

    XroadMember.
        where(sql_generator.sql, *sql_generator.params).
        joins(:member_class)
  end

  def self.get_searchable_columns
    return [
      "security_server_clients.name",
      "member_classes.code",
      "security_server_clients.member_code"
    ]
  end

  def self.get_by_code_relation(member_class, member_code)
    return XroadMember.joins(:member_class).where(
        :member_classes => {:code => member_class},
        :member_code => member_code)
  end

  def self.get_used_servers_relation(member_id)
    client_ids = [member_id]
    client_ids << Subsystem.where(:xroad_member_id => member_id).map {|x| x.id}

    return ServerClient.
        where(:security_server_client_id => client_ids).
        joins(:security_server_client).
        joins(:security_server)
  end

  def self.get_management_requests_relation(member_class, member_code)
    request_ids = get_management_request_ids(member_class, member_code)

    return Request.where(:id => request_ids)
  end

  def self.get_management_request_ids(member_class, member_code)
    result = []

    [:sec_serv_user, :security_server].each do |each|
      ids = Request.joins(each).where(
        :identifiers => {
          :member_class => member_class,
          :member_code => member_code
        }
      ).map { |x| x.id }

      result.push(*ids)
    end

    return result
  end

  def self.get_used_servers_as_json(raw_server_clients)
    servers_as_json = []

    raw_server_clients.each do |each|
      server = each.security_server

      server_to_add = {
        :id => server.id,
        :server => server.server_code,
        :client_subsystem_code => each.security_server_client.subsystem_code,
        :owner_id => server.owner.id,
        :owner_name => server.owner.name,
        :owner_class => server.owner.member_class.code,
        :owner_code => server.owner.member_code
      }

      servers_as_json << server_to_add
    end

    return servers_as_json
  end

  # Callback methods - start

  def save_identifier(record)
    identifier = ClientId.from_parts(
        SystemParameter.instance_identifier,
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

  # Callback methods - end
end
