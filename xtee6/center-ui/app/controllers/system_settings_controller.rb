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

class SystemSettingsController < ApplicationController

  def index
    authorize!(:view_system_settings)

    @instance_identifier = SystemParameter.instance_identifier

    @central_server_address = SystemParameter.central_server_address

    @service_provider_id =
      SystemParameter.management_service_provider_id

    @service_provider_name = XroadMember.get_name(
      @service_provider_id.memberClass,
      @service_provider_id.memberCode) if @service_provider_id

    @security_server_owners_group = SystemParameter.security_server_owners_group

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

    service_provider_name = XroadMember.get_name(
      params[:providerClass], params[:providerCode])

    audit_log_data[:serviceProviderIdentifier] =
      SystemParameter.management_service_provider_id
    audit_log_data[:serviceProviderName] = service_provider_name

    render_json({
      :id => SystemParameter.management_service_provider_id.toString,
      :name => service_provider_name
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
      :code => [:required],
      :description => [:required]
    })

    audit_log_data[:code] = params[:code]
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

    MemberClass.find_each do |member_class|
      if member_class.code.upcase == params[:code].upcase

        if member_class.xroad_members.any?
          raise t("system_settings.member_class_has_members", {
            :code => member_class.code.upcase
          })
        end

        member_class.destroy
        break
      end
    end

    render_json(read_member_classes)
  end

  private

  def read_services_addresses
    @wsdl_address =
      "http://#{SystemParameter.central_server_address}/managementservices.wsdl"

    @services_address =
      "http://#{SystemParameter.central_server_address}:4400/managementservice/"
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
