#
# The MIT License
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

class SystemSettingsController < ApplicationController

  def index
    authorize!(:view_system_settings)

    @instance_identifier = SystemParameter.instance_identifier

    @central_server_address = SystemParameter.central_server_address

    @service_provider_id =
      SystemParameter.management_service_provider_id

    @service_provider_name = XRoadMember.get_name(
      @service_provider_id.memberClass,
      @service_provider_id.memberCode) if @service_provider_id

    @security_server_owners_group = SystemParameter.security_server_owners_group

    read_service_provider_security_servers

    read_services_addresses
  end

  def central_server_address_edit
    audit_log("Edit central server address", audit_log_data = {})

    authorize!(:view_system_settings)

    validate_params({
      :centralServerAddress => [:required, :host]
    })

    audit_log_data[:address] = params[:centralServerAddress]

    SystemParameter.find_or_initialize(
      SystemParameter::CENTRAL_SERVER_ADDRESS, params[:centralServerAddress])

    begin
      ConfigurationSource.get_source_by_type(
        ConfigurationSource::SOURCE_TYPE_INTERNAL).generate_anchor
      notice(t("configuration_management.sources.internal_anchor_generated"))
    rescue
      error(t("configuration_management.sources.internal_anchor_error",
        :reason => $!.message))
    end

    begin
      ConfigurationSource.get_source_by_type(
        ConfigurationSource::SOURCE_TYPE_EXTERNAL).generate_anchor
      notice(t("configuration_management.sources.external_anchor_generated"))
    rescue
      error(t("configuration_management.sources.external_anchor_error",
        :reason => $!.message))
    end

    read_services_addresses

    render_json({
      :wsdl_address => @wsdl_address,
      :services_address => @services_address
    })
  end

  def service_provider_edit
    audit_log("Edit provider of management services", audit_log_data = {})

    authorize!(:view_system_settings)

    validate_params({
      :providerClass => [:required],
      :providerCode => [:required],
      :providerSubsystem => []
    })

    SystemParameter.find_or_initialize(
      SystemParameter::MANAGEMENT_SERVICE_PROVIDER_CLASS, params[:providerClass])

    SystemParameter.find_or_initialize(
      SystemParameter::MANAGEMENT_SERVICE_PROVIDER_CODE, params[:providerCode])

    SystemParameter.find_or_initialize(
      SystemParameter::MANAGEMENT_SERVICE_PROVIDER_SUBSYSTEM,
      params[:providerSubsystem])

    service_provider_name = XRoadMember.get_name(
      params[:providerClass], params[:providerCode])

    audit_log_data[:serviceProviderIdentifier] =
      SystemParameter.management_service_provider_id
    audit_log_data[:serviceProviderName] = service_provider_name

    read_service_provider_security_servers

    provider_id = SystemParameter.management_service_provider_id

    render_json({
      :id => provider_id.toString,
      :member_class => provider_id.memberClass,
      :member_code => provider_id.memberCode,
      :subsystem_code => provider_id.subsystemCode,
      :name => service_provider_name,
      :security_servers => @service_provider_security_servers
    })
  end

  def service_provider_register
    audit_log("Register management service provider as security server client",
      audit_log_data = {})

    authorize!(:register_service_provider)

    validate_params({
      :serverCode => [:required],
      :ownerClass => [:required],
      :ownerCode => [:required],
      :memberClass => [:required],
      :memberCode => [:required],
      :subsystemCode => []
    })

    if params[:subsystemCode] && params[:subsystemCode].empty?
      params[:subsystemCode] = nil
    end

    java_client_id = JavaClientId.create(
      SystemParameter.instance_identifier,
      params[:memberClass],
      params[:memberCode],
      params[:subsystemCode])

    audit_log_data[:serverCode] = params[:serverCode]
    audit_log_data[:ownerClass] = params[:ownerClass]
    audit_log_data[:ownerCode] = params[:ownerCode]
    audit_log_data[:clientIdentifier] = java_client_id

    client_id = ClientId.from_parts(
      SystemParameter.instance_identifier,
      params[:memberClass],
      params[:memberCode],
      params[:subsystemCode])

    server_id = SecurityServerId.from_parts(
        SystemParameter.instance_identifier,
        params[:ownerClass],
        params[:ownerCode],
        params[:serverCode])

    ClientRegRequest \
      .new_management_service_provider_request(server_id, client_id) \
      .register_management_service_provider

    logger.debug("Management service provider reg request " \
        "'#{request.inspect}' registered successfully")

    notice(t("system_settings.service_provider_registered", {
      :client_id => client_id, :server_id => server_id
    }))

    read_service_provider_security_servers

    render_json({
      :security_servers => @service_provider_security_servers
    })
  end

  def member_classes
    authorize!(:view_system_settings)

    validate_params

    render_json(read_member_classes)
  end

  def member_class_add
    audit_log("Add member class", audit_log_data = {})

    authorize!(:view_system_settings)

    validate_params({
      :code => [:required, :identifier],
      :description => [:required]
    })

    audit_log_data[:code] = params[:code].upcase
    audit_log_data[:description] = params[:description]

    MemberClass.find_each do |member_class|
      if member_class.code.upcase == params[:code].upcase
        raise t("system_settings.member_class_already_exists")
      end
    end

    MemberClass.create!({
      :code => params[:code].upcase,
      :description => params[:description]
    })

    render_json(read_member_classes)
  end

  def member_class_edit
    audit_log("Edit member class description", audit_log_data = {})

    authorize!(:view_system_settings)

    validate_params({
      :code => [:required],
      :description => [:required]
    })

    audit_log_data[:code] = params[:code]
    audit_log_data[:description] = params[:description]

    MemberClass.find_each do |member_class|
      if member_class.code.upcase == params[:code].upcase
        member_class.update_attributes!({
          :description => params[:description]
        })

        break
      end
    end

    render_json(read_member_classes)
  end

  def member_class_delete
    audit_log("Delete member class", audit_log_data = {})

    authorize!(:view_system_settings)

    validate_params({
      :code => [:required]
    })

    audit_log_data[:code] = params[:code]

    MemberClass.delete(params[:code])

    render_json(read_member_classes)
  end

  private

  def read_service_provider_security_servers
    @service_provider_security_servers = nil

    service_provider_id = SystemParameter.management_service_provider_id

    unless service_provider_id
      return
    end

    security_server_client = nil
    security_servers = []

    if service_provider_id.subsystemCode
      security_server_client = Subsystem.find_by_code(
        service_provider_id.memberClass,
        service_provider_id.memberCode,
        service_provider_id.subsystemCode)
    else
      security_server_client = XRoadMember.find_by_code(
        service_provider_id.memberClass,
        service_provider_id.memberCode)

      if security_server_client.owned_servers
        security_servers.concat(security_server_client \
          .owned_servers.map { |ss| ss.get_identifier })
      end
    end

    security_servers.concat(ServerClient \
      .where(:security_server_client_id => security_server_client.id) \
      .joins(:security_server) \
      .map { |ss| ss.security_server.get_identifier })

    @service_provider_security_servers = security_servers.join("; ")
  end

  def read_services_addresses
    @wsdl_address =
      "http://#{SystemParameter.central_server_address}/managementservices.wsdl"

    @services_address =
      "https://#{SystemParameter.central_server_address}:4002/managementservice/manage/"
  end

  def read_member_classes
    member_classes = []

    MemberClass.find_each do |member_class|
      member_classes << {
        :code => member_class.code,
        :description => member_class.description,
      }
    end

    member_classes
  end
end
