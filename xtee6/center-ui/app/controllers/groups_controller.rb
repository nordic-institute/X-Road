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

class GroupsController < ApplicationController
  before_filter :verify_get, :only => [
      :global_groups_refresh,
      :group_members,
      :addable_members,
      :get_member_count,
      :find_by_id]

  before_filter :verify_post, :only => [
      :group_add,
      :group_edit_description,
      :delete_group,
      :remove_selected_members,
      :add_members_to_group,
      :add_all_clients_to_group]

  before_filter :init_owners_group_code, :only => [
      :global_groups_refresh,
      :delete_group,
      :remove_selected_members,
      :add_members_to_group,
      :add_all_clients_to_group]

  # -- Common GET methods - start ---

  def index
    authorize!(:view_global_groups)
  end

  def get_records_count
    render_json_without_messages(:count => GlobalGroup.count)
  end

  # -- Common GET methods - end ---

  # -- Specific GET methods - start ---

  def global_groups_refresh
    authorize!(:view_global_groups)

    searchable = params[:sSearch]

    query_params = get_list_query_params(
      get_group_list_column(get_sort_column_no))

    groups = GlobalGroup.get_groups(query_params)
    count = GlobalGroup.get_group_count(searchable)

    result = []
    groups.each do |each|
      member_count = each.member_count ? each.member_count : 0
      group_code = each.group_code

      result << {
        :id => each.id,
        :code => group_code,
        :member_count => member_count,
        :description => each.description,
        :updated => format_time(each.updated_at.localtime),
        :is_readonly => group_code == @owners_group_code
      }
    end
    render_data_table(result, count, params[:sEcho])
  end

  def group_members
    authorize!(:view_group_details)

    searchable = params[:sSearch]

    advanced_search_params =
        get_advanced_search_params(params[:advancedSearchParams])

    query_params = get_list_query_params(
      get_group_members_column(get_sort_column_no))

    searchable = advanced_search_params if advanced_search_params
    group_id = params[:groupId]

    group_members = GlobalGroupMember.
        get_group_members(group_id, query_params, advanced_search_params)
    count = GlobalGroupMember.get_group_member_count(group_id, searchable)

    result = []
    group_members.each do |each|
      member_id = each.group_member
      member_class = member_id.member_class
      member_code = member_id.member_code

      result << {
        :name => XroadMember.get_name(member_class, member_code),
        :member_code => member_code,
        :member_class => member_class,
        :subsystem => member_id.subsystem_code,
        :xroad => member_id.xroad_instance,
        :type => member_id.object_type,
        :added => format_time(member_id.created_at.localtime)
      }
    end

    render_data_table(result, count, params[:sEcho])
  end

  def addable_members
    authorize!(:view_group_details)

    s_echo = params[:sEcho]

    searchable = params[:sSearch]

    advanced_search_params =
        get_advanced_search_params(params[:advancedSearchParams])

    query_params = get_list_query_params(
        get_addable_members_column(get_sort_column_no))

    searchable = advanced_search_params if advanced_search_params

    show_members = params[:showMembersInSearchResult] == "true"
    group_id = params[:groupId]
    group = GlobalGroup.find(group_id)

    member_infos = show_members ?
        SecurityServerClient.get_clients(query_params, advanced_search_params):
        SecurityServerClient.get_remaining_clients_for_group(group_id,
            query_params, advanced_search_params)

    count = show_members ?
        SecurityServerClient.get_clients_count(searchable):
        SecurityServerClient.get_remaining_clients_count(group_id, searchable)

    result = []
    member_infos.each do |each|
      client_id = each[:identifier]
      belongs_to_group = show_members ? group.has_member?(client_id) : false

      result << {
        :name => each[:name],
        :member_code => client_id.member_code,
        :member_class => client_id.member_class,
        :subsystem => client_id.subsystem_code,
        :xroad => client_id.xroad_instance,
        :type => client_id.object_type,
        :belongs_to_group => belongs_to_group
      }
    end

    render_data_table(result, count, s_echo)
  end

  def get_member_count
    member_count = GlobalGroup.get_member_count(params[:groupId])
    render_json({:member_count => member_count})
  end

  def find_by_id
    group = GlobalGroup.find(params[:groupId])
    group_as_json = {
      :id => group.id,
      :code => group.group_code,
      :description => group.description,
    }

    render_json(group_as_json)
  end

  # -- Specific GET methods - end ---

  # -- Specific POST methods - start ---

  def group_add
    audit_log("Add global group", audit_log_data = {})

    authorize!(:add_global_group)

    validate_description()

    code = params[:code]
    description = params[:description]

    audit_log_data[:code] = code
    audit_log_data[:description] = description

    GlobalGroup.add_group(code, description)
    render_json({})
  end

  def group_edit_description
    audit_log("Edit global group description", audit_log_data = {})

    authorize!(:edit_group_description)

    group = GlobalGroup.find(params[:groupId])

    audit_log_data[:code] = group.group_code
    audit_log_data[:description] = params[:description]

    validate_description

    GlobalGroup.update_description(params[:groupId], params[:description])

    notice(t("groups.change_description"));
    render_json();
  end

  def delete_group
    audit_log("Delete global group", audit_log_data = {})

    authorize!(:delete_group)

    group = GlobalGroup.find(params[:groupId])

    audit_log_data[:code] = group.group_code
    audit_log_data[:description] = group.description

    verify_composition_editability(group)

    group.destroy

    notice(t("groups.delete"))

    render_json
  end

  def remove_selected_members
    audit_log("Remove members from global group", audit_log_data = {})

    authorize!(:add_and_remove_group_members)

    raw_member_ids = params[:removableMemberIds].values
    group = GlobalGroup.find(params[:groupId])

    audit_log_data[:code] = group.group_code
    audit_log_data[:description] = group.description
    audit_log_data[:memberIdentifiers] = []

    verify_composition_editability(group)

    raw_member_ids.each do |each|
      member_id = ClientId.from_parts(
          each[:xRoadInstance],
          each[:memberClass],
          each[:memberCode],
          each[:subsystemCode]
      )

      audit_log_data[:memberIdentifiers] << JavaClientId.create(
        each[:xRoadInstance], each[:memberClass], each[:memberCode],
        each[:subsystemCode].blank? ? nil : each[:subsystemCode])

      logger.debug(
          "Removing member '#{member_id}' from global group '#{group.inspect}'")
      group.remove_member(member_id);
    end

    notice(t("groups.delete_selected_members"))

    render_json
  end

  def add_members_to_group
    audit_log("Add members to global group", audit_log_data = {})

    authorize!(:add_and_remove_group_members)

    group = GlobalGroup.find(params[:groupId])

    audit_log_data[:code] = group.group_code
    audit_log_data[:description] = group.description
    audit_log_data[:memberIdentifiers] = []

    verify_composition_editability(group)

    selected_members = params[:selectedMembers].values

    selected_members.each do |each|
      new_member_id = ClientId.from_parts(
          each[:xroad],
          each[:member_class],
          each[:member_code],
          each[:subsystem]
      )

      audit_log_data[:memberIdentifiers] << JavaClientId.create(
        each[:xroad], each[:member_class], each[:member_code],
        each[:subsystem].blank? ? nil : each[:subsystemCode])

      group.add_member(new_member_id)
    end

    render_json
  end

  # TODO Add test for it!
  def add_all_clients_to_group
    audit_log("Add members to global group", audit_log_data = {})

    authorize!(:add_and_remove_group_members)

    group = GlobalGroup.find(params[:groupId])
    advanced_search_params =
        get_advanced_search_params(params[:advancedSearchParams])
    query_params = ListQueryParams.new(
      get_addable_members_column(0), # Does not matter which column
      "asc",
      0,
      SecurityServerClient.count,
      params[:searchable]
    )

    audit_log_data[:code] = group.group_code
    audit_log_data[:description] = group.description
    audit_log_data[:memberIdentifiers] = []

    member_infos = SecurityServerClient.get_remaining_clients_for_group(
        group.id, query_params, advanced_search_params)

    member_infos.each do |each|
      member_id = each[:identifier]
      group.add_member(member_id)

      audit_log_data[:memberIdentifiers] << JavaClientId.create(
        member_id.xroad_instance,
        member_id.member_class,
        member_id.member_code,
        member_id.subsystem_code.blank? ? nil : member_id.subsystem_code)
    end

    render_json
  end

  # -- Specific POST methods - end ---

  private

  def validate_description
    return unless params[:description].blank?()

    raise t("groups.description_blank")
  end

  def init_owners_group_code
    @owners_group_code = SystemParameter.security_server_owners_group
  end

  def verify_composition_editability(group)
    if group.group_code == @owners_group_code
      raise "Cannot perform this action on server owners group."
    end
  end

  def get_group_list_column(index)
    case index
    when 0
      return 'group_code'
    when 1
      return 'description'
    when 2
      return 'member_count'
    when 3
      return 'created_at'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end

  def get_group_members_column(index)
    case index
    when 0
      return 'security_server_clients.name'
    when 1
      return 'identifiers.member_code'
    when 2
      return 'identifiers.member_class'
    when 3
      return 'identifiers.subsystem_code'
    when 4
      return 'identifiers.xroad_instance'
    when 5
      return 'identifiers.object_type'
    when 6
      return 'identifiers.created_at'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end

  def get_addable_members_column(index)
    case index
    when 0
      return 'security_server_client_names.name'
    when 1
      return 'identifiers.member_code'
    when 2
      return 'identifiers.member_class'
    when 3
      return 'identifiers.subsystem_code'
    when 4
      return 'identifiers.xroad_instance'
    when 5
      return 'identifiers.object_type'
    else
      raise "Index '#{index}' has no corresponding column."
    end

  end
end
