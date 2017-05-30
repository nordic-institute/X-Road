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

class CentralServicesController < ApplicationController

  before_filter :verify_get, :only => [
    :services_refresh,
    :search_providers]

  before_filter :verify_post, :only => [
    :save_service,
    :update_service,
    :delete_service,
    :delete_target_service]

  # -- Common GET methods - start ---

  def index
    authorize!(:view_central_services)
  end

  def get_records_count
    render_json_without_messages(:count => CentralService.count)
  end

  def can_see_details
    render_details_visibility(:view_central_service_details)
  end

  # -- Common GET methods - end ---

  # -- Specific GET methods - start ---

  def services_refresh
    authorize!(:view_central_services)

    searchable = params[:sSearch]

    query_params = get_list_query_params(
      get_service_column(get_sort_column_no))

    central_services = CentralService.
        get_central_services(query_params)
    count = CentralService.get_service_count(searchable)

    result = []
    central_services.each do |each|
      service_id = each.target_service
      provider_name = service_id ?
          XroadMember.get_name(service_id.member_class, service_id.member_code) :
          ""
      result << {
        :central_service_code => each.service_code,
        :provider_name => provider_name,
        :id_service_code => service_id ? service_id.service_code : "",
        :id_service_version => service_id ? service_id.service_version : "",
        :id_provider_code => service_id ? service_id.member_code : "",
        :id_provider_class => service_id ? service_id.member_class : "",
        :id_provider_subsystem => service_id ? service_id.subsystem_code : ""
      }
    end

    render_data_table(result, count, params[:sEcho])
  end

  def search_providers
    authorize!(:view_central_services)

    searchable = params[:sSearch]

    query_params = get_list_query_params(
        get_provider_sort_column(get_sort_column_no()))

    advanced_search_params =
        get_advanced_search_params(params[:providerSearchParams])

    providers =
      SecurityServerClient.get_clients(query_params, advanced_search_params)

    count = SecurityServerClient.get_clients_count(
        advanced_search_params, searchable)

    if !params[:allowZeroProviders] && providers.empty?
      raise t("central_services.add.provider_not_found")
    end

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

  # -- Specific GET methods - end ---

  # -- Specific POST methods - start ---

  def save_service
    audit_log("Add central service", audit_log_data = {})

    authorize!(:add_central_service)

    target_service = get_target_service_from_params()
    provider_id = get_provider_id(target_service[:code])

    audit_log_data[:serviceCode] = params[:serviceCode]
    audit_log_data[:targetServiceCode] = target_service[:code]
    audit_log_data[:targetServiceVersion] = target_service[:version]

    if provider_id
      audit_log_data[:providerIdentifier] = JavaClientId.create(
        provider_id.xroad_instance, provider_id.member_class,
        provider_id.member_code, provider_id.subsystem_code)
    end

    CentralService.save(
        params[:serviceCode],
        target_service,
        provider_id
    )

    render_json
  end

  def update_service
    audit_log("Edit central service", audit_log_data = {})

    authorize!(:edit_implementing_service)

    target_service = get_target_service_from_params
    provider_id = get_provider_id(target_service[:code])

    audit_log_data[:serviceCode] = params[:serviceCode]
    audit_log_data[:targetServiceCode] = target_service[:code]
    audit_log_data[:targetServiceVersion] = target_service[:version]
    audit_log_data[:providerIdentifier] = JavaClientId.create(
      provider_id.xroad_instance, provider_id.member_class,
      provider_id.member_code, provider_id.subsystem_code)

    CentralService.update(
        params[:serviceCode],
        target_service,
        provider_id
    )

    render_json()
  end

  def delete_service
    audit_log("Delete central service", audit_log_data = {})

    authorize!(:remove_central_service)

    audit_log_data[:serviceCode] = params[:serviceCode]

    CentralService.delete(params[:serviceCode])

    render_json
  end

  def delete_target_service
    authorize!(:remove_central_service)

    CentralService.delete_target_service(params[:serviceCode])
    render_json()
  end

  # -- Specific POST methods - end ---

  private

  def get_target_service_item(raw_item)
    return nil if !raw_item || raw_item.blank?

    return raw_item
  end

  def get_provider_id(target_service_code)
    provider_class = params[:targetProviderClass]
    provider_code = params[:targetProviderCode]

    if !provider_class.blank? &&
        !provider_code.blank? &&
        target_service_code.blank?
      raise t("central_services.add.provider_with_no_service_code")
    end

    target_service_code ?
        ClientId.from_parts(
            SystemParameter.instance_identifier,
            provider_class,
            provider_code,
            params[:targetProviderSubsystem]
        ) :
        nil
  end

  def get_target_service_from_params
    {
      :code => get_target_service_item(params[:targetServiceCode]),
      :version => get_target_service_item(params[:targetServiceVersion])
    }
  end

  def get_service_column(index)
    case(index)
    when 0
      return 'central_services.service_code'
    when 1
      return 'identifiers.service_code'
    when 2
      return 'identifiers.service_version'
    when 3
      return 'identifiers.member_class'
    when 4
      return 'identifiers.member_code'
    when 5
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
