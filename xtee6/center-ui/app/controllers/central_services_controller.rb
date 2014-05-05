class CentralServicesController < ApplicationController

  def index
    authorize!(:view_central_services)
  end

  def services_refresh
    authorize!(:view_central_services)

    searchable = params[:sSearch]

    advanced_search_params =
        get_advanced_search_params(params[:advancedSearchParams])

    query_params = get_list_query_params(
      get_service_column(get_sort_column_no))

    searchable = advanced_search_params if advanced_search_params

    central_services = CentralService.
        get_central_services(query_params, advanced_search_params)
    count = CentralService.get_service_count(searchable)

    result = []
    central_services.each do |each|
      service_id = each.target_service
      provider_name = service_id ? get_provider_name(service_id) : ""
      result << {
        :central_service_code => each.service_code,
        :provider_name => provider_name,
        :id_service_code => service_id ? service_id.service_code : "",
        :id_provider_code => service_id ? service_id.member_code : "",
        :id_provider_class => service_id ? service_id.member_class : "",
        :id_provider_subsystem => service_id ? service_id.subsystem_code : ""
      }
    end

    render_data_table(result, count, params[:sEcho])
  end

  def save_service
    authorize!(:add_central_service)

    target_service_code = get_target_service_code(params[:targetServiceCode])
    provider_id = get_provider_id(target_service_code)

    CentralService.save(
        params[:serviceCode],
        target_service_code,
        provider_id
    )

    render_json()
  end

  def update_service
    authorize!(:edit_implementing_service)

    target_service_code = get_target_service_code(params[:targetServiceCode])
    provider_id = get_provider_id(target_service_code)

    CentralService.update(
        params[:serviceCode],
        target_service_code,
        provider_id
    )

    render_json()
  end

  def delete_service
    authorize!(:remove_central_service)

    CentralService.delete(params[:serviceCode])
    render_json()
  end

  def delete_target_service
    authorize!(:remove_central_service)

    CentralService.delete_target_service(params[:serviceCode])
    render_json()
  end

  def search_providers
    authorize!(:view_central_services)

    searchable = get_advanced_search_params(params[:providerSearchParams])

    if !has_searchable_data(searchable)
      raise t("central_services.no_search_data")
    end

    query_params = get_list_query_params(
      get_provider_sort_column(get_sort_column_no))

    providers = SecurityServerClient.get_clients(query_params, searchable)
    count = SecurityServerClient.get_clients_count(searchable)

    raise t("central_services.add.provider_not_found") if providers.empty?

    result = []
    providers.each do |each|
      provider_id = each[:identifier]
      result << {
        :name => each[:name],
        :member_class => provider_id.member_class,
        :member_code => provider_id.member_code,
        :subsystem => provider_id.subsystem_code
      }
    end

    render_data_table(result, count, params[:sEcho])
  end

  def can_see_details
    render_details_visibility(:view_central_service_details)
  end

  def get_records_count
    render_json(:count => CentralService.count)
  end

  private

  def get_target_service_code(raw_code)
    return nil if !raw_code || raw_code.blank?

    raw_code
  end

  def get_provider_name(service_id)
    provider =
        SdsbMember.find_by_code(service_id.member_class, service_id.member_code)

    provider ? provider.name : nil
  end

  def get_provider_id(target_service_code)
    return nil unless target_service_code

    provider_class = params[:targetProviderClass]
    provider_code = params[:targetProviderCode]

    if provider_class && provider_code && !target_service_code
      raise t("central_services.add.provider_with_no_service_code")
    end

    target_service_code ?
        ClientId.from_parts(
            SystemParameter.sdsb_instance,
            provider_class,
            provider_code,
            params[:targetProviderSubsystem]
        ) :
        nil
  end

  def has_searchable_data(searchable)
    return searchable.name ||
        searchable.member_class ||
        searchable.member_code ||
        searchable.subsystem_code
  end

  def get_service_column(index)
    case(index)
    when 0
      return 'central_services.service_code'
    when 1
      return 'identifiers.service_code'
    when 2
      return 'identifiers.member_class'
    when 3
      return 'identifiers.member_code'
    when 4
      return 'identifiers.subsystem_code'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end

  def get_provider_sort_column(index)
    case(index)
    when 0
      return 'security_server_client_names.name'
    when 1
      return 'identifiers.member_code'
    when 2
      return 'identifiers.member_class'
    when 3
      return 'identifiers.subsystem_code'
    else
      raise "Index '#{index}' has no corresponding column."
    end
  end
end
