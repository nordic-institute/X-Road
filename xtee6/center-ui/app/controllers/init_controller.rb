java_import Java::ee.ria.xroad.commonui.SignerProxy

class InitController < ApplicationController

  skip_before_filter :check_conf, :read_server_id

  def index
    if request.xhr?
      # come back without ajax
      render_redirect(root_path, "common.initialization_required")
      return
    end

    if cannot?(:init_config)
      raise t('init.not_authorized')
    end

    if initialized?
      raise t('init.already_initialized')
    end

    @instance_identifier = SystemParameter.instance_identifier
    @central_server_address = SystemParameter.central_server_address

    unless software_token_initialized?
      @init_software_token = true
    end
  end

  def init
    authorize!(:init_config)

    required = [:required]

    unless SystemParameter.instance_identifier
      init_instance_identifier = required
    end

    unless SystemParameter.central_server_address
      init_central_server_address = required << :host
    end

    unless software_token_initialized?
      init_software_token = required
    end

    unless init_instance_identifier ||
        init_central_server_address ||
        init_software_token
      raise t('init.already_initialized')
    end

    validate_params({
      :instance_identifier => init_instance_identifier || [],
      :central_server_address => init_central_server_address || [],
      :pin => init_software_token || [],
      :pin_repeat => init_software_token || []
    })

    if init_instance_identifier
      SystemParameter.create!(
        :key => SystemParameter::INSTANCE_IDENTIFIER,
        :value => params[:instance_identifier])
    end

    if init_central_server_address
      SystemParameter.create!(
        :key => SystemParameter::CENTRAL_SERVER_ADDRESS,
        :value => params[:central_server_address])
    end

    init_other_system_parameters

    if init_software_token
      if params[:pin] != params[:pin_repeat]
        raise t('init.mismatching_pins')
      else
        pin = Array.new
        params[:pin].bytes do |b|
          pin << b
        end

        SignerProxy::initSoftwareToken(pin.to_java(:char))
      end
    end

    render_json
  end

  private

  def init_other_system_parameters
    SystemParameter.find_or_initialize_by_key(
      SystemParameter::AUTH_CERT_REG_URL
    ).update_attributes!({
      :value => SystemParameter::DEFAULT_AUTH_CERT_REG_URL
    })

    GlobalGroup.find_or_initialize_by_group_code(
      SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP
    ).update_attributes!({
      :description => SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP_DESC
    })

    SystemParameter.find_or_initialize_by_key(
      SystemParameter::CONF_SIGN_ALGO_ID
    ).update_attributes!({
      :value => SystemParameter::DEFAULT_CONF_SIGN_ALGO_ID
    })

    SystemParameter.find_or_initialize_by_key(
      SystemParameter::CONF_HASH_ALGO_URI
    ).update_attributes!({
      :value => SystemParameter::DEFAULT_CONF_HASH_ALGO_URI
    })

    SystemParameter.find_or_initialize_by_key(
      SystemParameter::CONF_SIGN_CERT_HASH_ALGO_URI
    ).update_attributes!({
      :value => SystemParameter::DEFAULT_CONF_SIGN_CERT_HASH_ALGO_URI
    })

    SystemParameter.find_or_initialize_by_key(
      SystemParameter::SECURITY_SERVER_OWNERS_GROUP
    ).update_attributes!({
      :value => SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP
    })
  end
end
