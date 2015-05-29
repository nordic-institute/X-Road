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
    authorize!(:view_system_settings)

    validate_params({
      :centralServerAddress => [:required, :host]
    })

    SystemParameter.find_or_initialize_by_key(
      SystemParameter::CENTRAL_SERVER_ADDRESS
    ).update_attributes!({
      :value => params[:centralServerAddress]
    })

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
    authorize!(:view_system_settings)

    validate_params({
      :providerClass => [:required],
      :providerCode => [:required],
      :providerSubsystem => []
    })

    SystemParameter.find_or_initialize_by_key(
      SystemParameter::MANAGEMENT_SERVICE_PROVIDER_CLASS
    ).update_attributes!({
      :value => params[:providerClass]
    })

    SystemParameter.find_or_initialize_by_key(
      SystemParameter::MANAGEMENT_SERVICE_PROVIDER_CODE
    ).update_attributes!({
      :value => params[:providerCode]
    })

    SystemParameter.find_or_initialize_by_key(
      SystemParameter::MANAGEMENT_SERVICE_PROVIDER_SUBSYSTEM
    ).update_attributes!({
      :value => params[:providerSubsystem]
    })

    render_json({
      :id => SystemParameter.management_service_provider_id.toString,
      :name => XroadMember.get_name(
        params[:providerClass], params[:providerCode])
    })
  end

  def member_classes
    authorize!(:view_system_settings)

    validate_params

    render_json(read_member_classes)
  end

  def member_class_add
    authorize!(:view_system_settings)

    validate_params({
      :code => [:required],
      :description => [:required]
    })

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
    authorize!(:view_system_settings)

    validate_params({
      :code => [:required],
      :description => [:required]
    })

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
    authorize!(:view_system_settings)

    validate_params({
      :code => [:required]
    })

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
