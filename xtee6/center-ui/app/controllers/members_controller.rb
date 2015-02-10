class MembersController < ApplicationController
  include RequestsHelper
  include SecurityserversHelper
  include AuthCertHelper

  before_filter :verify_get, :only => [
      :index,
      :members_refresh,
      :owned_servers,
      :global_groups,
      :subsystems,
      :used_servers,
      :management_requests,
      :remaining_global_groups,
      :get_member_by_id,
      :subsystem_codes]

  before_filter :verify_post, :only => [
      :member_add,
      :member_edit,
      :delete,
      :delete_subsystem,
      :import_auth_cert,
      :add_new_owned_server_request,
      :add_new_server_client_request,
      :delete_server_client_request,
      :add_member_to_global_group,
      :delete_member_from_global_group]

  before_filter :init_owners_group_code, :only => [:global_groups]

  # -- Common GET methods - start ---

  def index
    if cannot?(:view_members)
      redirect_to :controller => :configuration_management
    end
  end

  def get_records_count
    render_json_without_messages(:count => SdsbMember.count)
  end

  def can_see_details
    render_details_visibility(:view_member_details)
  end

  # -- Common GET methods - end ---

  # -- Specific GET methods - start ---

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

  def member_search
    authorize!(:search_members)

    searchable = params[:sSearch]
    security_server_code = params[:securityServerCode]

    query_params = get_list_query_params(
      get_member_search_column(get_sort_column_no))

    advanced_search_params =
        get_advanced_search_params(params[:advancedSearchParams])

    providers = SecurityServerClient.get_addable_clients_for_server(
        security_server_code, query_params, advanced_search_params)
    count = SecurityServerClient.get_addable_clients_count(
        security_server_code, searchable, advanced_search_params)

    result = []
    providers.each do |each|
      provider_id = each[:identifier]

      result << {
        :name => each[:name],
        :member_class => provider_id.member_class,
        :member_code => provider_id.member_code,
        :subsystem_code => provider_id.subsystem_code,
        :sdsb_instance => provider_id.sdsb_instance,
        :type => provider_id.object_type
      }
    end

    render_data_table(result, count, params[:sEcho])
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

    render_json(SdsbMember.get_used_servers(member.id))
  end

  def management_requests
    authorize!(:view_member_details)

    member_class = params[:memberClass]
    member_code = params[:memberCode]

    query_params = get_list_query_params(
      get_management_requests_column(get_sort_column_no))

    requests = SdsbMember.get_management_requests(
        member_class, member_code, query_params)
    count = SdsbMember.get_management_requests_count(member_class, member_code)

    result = []
    add_requests_to_result(requests, result)

    render_data_table(result, count, params[:sEcho])
  end

  def remaining_global_groups
    authorize!(:view_member_details)

    remaining_groups = SdsbMember.get_remaining_global_groups(
        params[:memberClass], params[:memberCode], params[:subsystemCode])

    result = []

    remaining_groups.each do |each|
      result << {
        :code => each.group_code,
        :description => each.description
      }
    end

    render_json(result)
  end

  def get_member_by_id
    authorize!(:view_member_details)

    member = SdsbMember.find(params[:memberId])
    render_json_without_messages(get_all_member_data(member))
  end

  def subsystem_codes
    authorize!(:view_member_details)

    member = find_member(params[:memberClass], params[:memberCode])

    render_json(member.subsystem_codes())
  end

  # -- Specific GET methods - end ---

  # -- Specific POST methods - start ---

  def member_add
    authorize!(:add_new_member)

    member_class = params[:memberClass]
    member_code = params[:memberCode]

    SdsbMember.create!(
        :name => params[:memberName],
        :member_class => MemberClass.find_by_code(member_class),
        :member_code => member_code)

    notice(t("members.added",
        {:member_class => member_class, :member_code => member_code}))

    saved_member = SdsbMember.find_by_code(member_class, member_code)
    render_json(get_all_member_data(saved_member))
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

    cert_param = get_uploaded_file_param
    validate_auth_cert(cert_param)
    auth_cert_data = upload_cert(cert_param)

    notice(t("common.cert_imported"))

    upload_success(auth_cert_data,
        "SDSB_MEMBER_EDIT.uploadCallbackOwnedServerAuthCert")
  rescue RuntimeError => e
    error(e.message)
    upload_error(nil, "SDSB_MEMBER_EDIT.uploadCallbackOwnedServerAuthCert")
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
    render_json()
  end

  def add_new_server_client_request
    authorize!(:add_security_server_client_reg_request)

    member_class = params[:memberClass]
    member_code = params[:memberCode]

    subsystem_code_param = params[:subsystemCode]
    subsystem_code = get_subsystem_code(subsystem_code_param)

    # We use it outside condition as member must always exist.
    member = find_member(member_class, member_code)

    if must_save_subsystem(member_class, member_code, subsystem_code)
      Subsystem.create!(
              :sdsb_member => member,
              :subsystem_code => subsystem_code)
    end

    server_id = SecurityServerId.from_parts(
        SystemParameter.instance_identifier,
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

    render_json()
  end

  def delete_server_client_request
    authorize!(:add_security_server_client_reg_request)

    subsystem_code_param = params[:subsystemCode]
    subsystem_code = get_subsystem_code(subsystem_code_param)

    server_id = SecurityServerId.from_parts(
        SystemParameter.instance_identifier,
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

    render_json()
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

  # -- Specific POST methods - end ---

  private

  def init_owners_group_code
    @owners_group_code = SystemParameter.security_server_owners_group
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
      group_code = group.group_code

      result << {
        :group_id => group.id,
        :group_code => group_code,
        :subsystem => member_id.subsystem_code,
        :identifier => member_id.to_s,
        :added_to_group => format_time(member_id.created_at.localtime),
        :is_readonly => group_code == @owners_group_code
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
        :owner_code => server.owner.member_code,
        :identifier => server.get_identifier()
      }
    end

    return servers_as_json
  end

  def get_subsystems(member)
    subsystems = []

    member.subsystems.each do |subsystem|
      used_servers = []

      subsystem.security_servers.each do |used_server|
        used_servers << {
          :id => used_server.id,
          :server_code => used_server.server_code
        }
      end

      subsystems << {
        :subsystem_code => subsystem.subsystem_code,
        :used_servers => used_servers.to_a
      }
    end

    return subsystems
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

    return member
  end

  def find_global_group(group_code)
    global_group = GlobalGroup.find_by_code(group_code)
    raise "Global group with code '#{group_code}' not found" unless global_group

    return global_group
  end

  def get_client_id(member_class, member_code, subsystem_code)
    ClientId.from_parts(
        SystemParameter.instance_identifier,
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

  def get_member_search_column(index)
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
      return 'identifiers.sdsb_instance'
    when 5
      return 'identifiers.object_type'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end

  def get_used_servers_column(index)
    case index
    when 0
      return 'security_servers.server_code'
    when 1
      return 'security_server_clients.subsystem_code'
    when 2
      return 'security_server_clients.name'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end

  def get_management_requests_column(index)
    case index
    when 0
      return 'requests.id'
    when 1
      return 'requests.type'
    when 2
      return 'requests.created_at'
    when 3
      return 'requests.processing_status'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end
end
