require 'ruby_cert_helper'
require 'set'

class MembersController < ApplicationController
  include RubyCertHelper
  include RequestsHelper
  include SecurityserversHelper
  include AuthCertHelper

  # TODO: Is this necessary?
  before_filter :load_members, :except => [:member_add]

  def members_refresh
    authorize!(:view_members)

    searchable = params[:sSearch]

    query_params = get_list_query_params(
      get_column(get_sort_column_no))

    members = SdsbMember.get_members(query_params)
    count = SdsbMember.get_member_count(searchable)

    result = []
    members.each do |each|
      result << get_all_member_data(each)
    end

    render_data_table(result, count, params[:sEcho])
  end

  def member_add
    authorize!(:add_new_member)

    SdsbMember.create!(
        :name => params[:memberName],
        :member_class => MemberClass.find_by_code(params[:memberClass]),
        :member_code => params[:memberCode])

    render_json({})
  end

  def member_edit
    authorize!(:edit_member_name_and_admin_contact)

    member_to_update = find_member(params[:memberClass], params[:memberCode])

    member_to_update.update_attributes!(
        :name => params[:memberName],
        :administrative_contact => params[:adminContact])

    render_json({})
  end

  def delete
    authorize!(:delete_member)

    member_to_delete = find_member(params[:memberClass], params[:memberCode])

    SdsbMember.destroy(member_to_delete)

    render_json({})
  end

  def owned_servers
    authorize!(:view_member_details)

    member = find_member(params[:memberClass], params[:memberCode])

    render_json(get_servers_as_json(member.owned_servers))
  end

  def global_groups
    result = get_global_groups_as_json(params[:memberClass],
        params[:memberCode])

    render_json(result)
  end

  def subsystems
    authorize!(:view_member_details)

    member = find_member(params[:memberClass], params[:memberCode])

    render_json(get_subsystems(member))
  end

  def used_servers
    authorize!(:view_member_details)

    member = find_member(params[:memberClass], params[:memberCode])

    response_json = get_servers_as_json(member.security_servers)

    response_json += get_subsystems_as_json(member.subsystems)

    render_json(response_json)
  end

  def management_requests
    authorize!(:view_member_details)

    member_class = params[:memberClass]
    member_code = params[:memberCode]

    user_requests = Request.joins(:sec_serv_user).where(
      :identifiers => {
        :member_class => member_class,
        :member_code => member_code
      }
    )

    result = []
    add_requests_to_result(user_requests, result)

    owned_server_requests = Request.joins(:security_server).where(
      :identifiers => {
        :member_class => member_class,
        :member_code => member_code
      }
    )

    add_requests_to_result(owned_server_requests, result)

    result.uniq!

    render_json(result)
  end

  def delete_subsystem
    authorize!(:remove_member_subsystem)

    member = find_member(params[:memberClass], params[:memberCode])
    subsystem = Subsystem.where(:sdsb_member_id => member.id,
        :subsystem_code => params[:subsystemCode]).first

    Subsystem.destroy(subsystem)
    render_json(get_subsystems(member))
  end

  def import_auth_cert
    authorize!(:add_security_server_reg_request)

    auth_cert_data = upload_cert(params[:auth_cert_file])

    notice(t("common.cert_imported"))

    upload_success(auth_cert_data, "uploadCallbackOwnedServerAuthCert")
  rescue RuntimeError => e
    error(e.message)
    upload_error(nil, "uploadCallbackOwnedServerAuthCert")
  end

  def add_new_owned_server_request
    authorize!(:add_security_server_reg_request)

    owner = find_member(params[:ownerClass], params[:ownerCode])
    server_code = params[:serverCode]

    if !server_code || server_code.empty?
      raise t("members.server_code_blank")
    end

    potentially_existing_server = SecurityServer.where(
        :sdsb_member_id => owner.id,
        :server_code => server_code).first

    if potentially_existing_server
      raise t("members.server_already_exists",
          { :member_class => owner.member_class.code,
            :member_code => owner.member_code,
            :server_code => server_code})
    end

    server_data = {
        :owner_class => owner.member_class.code,
        :owner_code => owner.member_code,
        :server_code => server_code
    }

    request = add_auth_cert_reg_request(server_data, params[:tempCertId])

    notice(t("members.auth_cert_request_added",
        {:security_server => request.security_server}))
    render :partial => "application/messages"
  end

  def cancel_new_owned_server_request
    authorize!(:add_security_server_reg_request)

    render :partial => "application/messages"
  end

  def get_all_securityservers
    authorize!(:view_member_details)

    render_json(get_all_servers_as_json)
  end

  def add_new_server_client_request
    authorize!(:add_security_server_client_reg_request)

    member_class = params[:memberClass]
    member_code = params[:memberCode]

    subsystem_code_param = params[:subsystemCode]
    subsystem_code = get_subsystem_code(subsystem_code_param)

    if must_save_subsystem(member_class, member_code, subsystem_code)
      member = find_member(member_class, member_code)
      Subsystem.create!(
              :sdsb_member => member,
              :subsystem_code => subsystem_code)
    end

    server_id = SecurityServerId.from_parts(
        SystemParameter.sdsb_instance,
        params[:ownerClass],
        params[:ownerCode],
        params[:serverCode])

    client_id = get_client_id(member_class, member_code, subsystem_code)

    client_reg_request = ClientRegRequest.new(
         :security_server => server_id,
         :sec_serv_user => client_id,
         :origin => Request::CENTER)

    client_reg_request.register()

    logger.debug("Client reg request '#{client_reg_request.inspect}'"\
            "registered successfully")

    notice(t("members.client_add_request_added", 
      {:client_id => client_id, :server_id => server_id}))

    render :partial => "application/messages"
  end

  def delete_server_client_request
    authorize!(:add_security_server_client_reg_request)

    subsystem_code_param = params[:subsystemCode]
    subsystem_code = get_subsystem_code(subsystem_code_param)

    server_id = SecurityServerId.from_parts(
        SystemParameter.sdsb_instance,
        params[:ownerClass],
        params[:ownerCode],
        params[:serverCode])

    client_id = get_client_id(
        params[:memberClass],
        params[:memberCode],
        subsystem_code)

    client_deletion_request =  ClientDeletionRequest.new(
        :security_server => server_id,
        :sec_serv_user => client_id,
        :comments => "Client '#{client_id.to_s}' deletion",
        :origin => Request::CENTER)

    client_deletion_request.register()

    logger.debug("Client deletion request '#{client_deletion_request.inspect}'"\
            "registered successfully")

    notice(t("members.client_deletion_request_added",
        {:client_id => client_id, :server_id => server_id}))

    render :partial => "application/messages"
  end

  def remaining_global_groups
    authorize!(:view_member_details)

    remaining_groups = SdsbMember.get_remaining_global_groups(
        params[:memberClass], params[:memberCode])

    result = []

    remaining_groups.each do |each|
      result << {
        :code => each.group_code,
        :description => each.description
      }
    end

    render_json(result)
  end

  def add_member_to_global_group
    authorize!(:add_and_remove_group_members)

    group_code = params[:groupCode]
    member_class = params[:memberClass]
    member_code = params[:memberCode]

    global_group = find_global_group(group_code)

    client_id = get_client_id(member_class, member_code, params[:subsystemCode])

    global_group.add_member(client_id)

    notice(t("members.member_added_to_global_group",
        {:member_code => member_code, :group_code => group_code}))

    render_json(get_global_groups_as_json(member_class, member_code))
  end

  def delete_member_from_global_group
    authorize!(:add_and_remove_group_members)

    group_code = params[:groupCode]
    member_class = params[:memberClass]
    member_code = params[:memberCode]

    global_group = find_global_group(group_code)
    client_id = get_client_id(member_class, member_code, params[:subsystemCode])

    global_group.remove_member(client_id)

    notice(t("members.member_deleted_from_global_group",
        {:member_code => member_code, :group_code => group_code}))

    render_json(get_global_groups_as_json(member_class, member_code))
  end

  def get_member_by_id
    authorize!(:view_member_details)

    member = SdsbMember.find(params[:memberId])
    render_json(get_all_member_data(member))
  end

  def subsystem_codes
    authorize!(:view_member_details)

    member = find_member(params[:memberClass], params[:memberCode])
    subsystem_codes = []

    member.subsystems.each do |each|
      subsystem_codes << each.subsystem_code
    end

    render_json(subsystem_codes)
  end

  def can_see_details
    render_details_visibility(:view_member_details)
  end

  def get_records_count
    render_json(:count => SdsbMember.count)
  end

  private

  def get_members_as_json
    members_as_json = []

    @members.each do |member|
      members_as_json << get_all_member_data(member)
    end

    members_as_json
  end

  def get_all_member_data(member)
    {
      :id => member.id,
      :name => member.name,
      :member_class => member.member_class.code,
      :member_code => member.member_code,
      :admin_contact => member.administrative_contact
    }
  end

  def get_global_groups_as_json(member_class, member_code)
    group_members = SdsbMember.get_global_group_members(
        member_class, member_code)

    result = []

    group_members.each do |each|
      group = each.global_group
      member_id = each.group_member
      result << {
        :group_id => group.id,
        :group_code => group.group_code,
        :subsystem => member_id.subsystem_code,
        :identifier => member_id.to_s,
        :added_to_group => format_time(member_id.created_at.localtime)
      }
    end
    result
  end

  def get_servers_as_json(servers)
    servers_as_json = []

    servers.each do |server|
      servers_as_json << {
        :id => server.id,
        :server => server.server_code,
        :client_subsystem_code => "",
        :owner_id => server.owner.id,
        :owner_name => server.owner.name,
        :owner_class => server.owner.member_class.code,
        :owner_code => server.owner.member_code
      }
    end

    servers_as_json
  end

  def get_subsystems_as_json(subsystems)
    # TODO: Is it normal that without Set duplicate subsystems may occur?
    subsystems_as_json = Set.new

    subsystems.each do |subsystem|
      subsystem.security_servers.each do |server|
        subsystems_as_json << {
          :id => server.id,
          :server => server.server_code,
          :client_subsystem_code => subsystem.subsystem_code,
          :owner_id => server.owner.id,
          :owner_name => server.owner.name,
          :owner_class => server.owner.member_class.code,
          :owner_code => server.owner.member_code
        }
      end
    end

    subsystems_as_json.to_a
  end

  def get_subsystems(member)
    subsystems = []

    member.subsystems.each do |subsystem|
      # TODO: Is it normal that without Set duplicate server codes may occur?
      used_servers = Set.new

      subsystem.security_servers.each do |used_server|
        used_servers << {
          :id => used_server.id,
          :server_code => used_server.server_code
        }
      end

      subsystems << {
        :subsystem_code => subsystem.subsystem_code,
        :used_servers => used_servers.to_a
        # TODO: Maybe give additionally mapping with exact coordinates of
        # security server or make used_servers hierarchical to provide all data
        # that is needed to identify security server?
      }
    end
    subsystems
  end

  def load_members
    @members = SdsbMember.find(:all)
  end

  def get_subsystem_code(subsystem_code_param)
    !subsystem_code_param || subsystem_code_param.empty? ?
            nil : subsystem_code_param
  end

  def must_save_subsystem(member_class, member_code, subsystem_code)
    subsystem_code &&
        !Subsystem.find_by_code(member_class, member_code, subsystem_code)
  end

  def find_member(member_class, member_code)
    member = SdsbMember.find_by_code(member_class, member_code)

    raise "Member with member class '#{member_class}' and member code"\
         " '#{member_code}' not found." unless member

    member
  end

  def find_global_group(group_code)
    global_group = GlobalGroup.find_by_code(group_code)
    raise "Global group with code '#{group_code}' not found" unless global_group

    global_group
  end

  def get_client_id(member_class, member_code, subsystem_code)
    ClientId.from_parts(
        SystemParameter.sdsb_instance,
        member_class,
        member_code,
        subsystem_code
    )
  end

  def get_column(index)
    case index
    when 0
      return 'name'
    when 1
      return 'member_classes.code'
    when 2
      return 'member_code'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end
end
