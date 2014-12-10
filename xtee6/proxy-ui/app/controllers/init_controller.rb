require 'net/http'

java_import Java::ee.cyber.sdsb.common.conf.globalconf.ConfigurationAnchor
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.ClientType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.ServerConfType
java_import Java::ee.cyber.sdsb.common.identifier.ClientId
java_import Java::ee.cyber.sdsb.commonui.SignerProxy

class InitController < ApplicationController

  skip_around_filter :transaction, :only =>
    [:anchor_upload, :anchor_submit, :member_classes, :member_codes, :member_name]

  skip_before_filter :check_conf, :read_server_id, :read_owner_name

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

    unless globalconf_initialized?
      @init_anchor = true
    end

    unless serverconf_initialized?
      @init_serverconf = true
    end

    unless software_token_initialized?
      @init_software_token = true
    end

    if serverconf
      if serverconf.owner
        @owner_class = serverconf.owner.identifier.memberClass
        @owner_code = serverconf.owner.identifier.memberCode

        unless @init_anchor
          @owner_name = get_member_name(@owner_class, @owner_code)
        end
      end

      @server_code = serverconf.serverCode
    end
  end

  def anchor_upload
    authorize!(:init_config)

    validate_params({
      :anchor_upload_file => [:required]
    })

    anchor_details =
      save_temp_anchor_file(params[:anchor_upload_file].read)

    upload_success(anchor_details)
  end

  def anchor_init
    authorize!(:init_config)

    validate_params

    apply_temp_anchor_file

    notice(t('init.configuration_downloaded'))

    render_json
  end

  def serverconf_init
    authorize!(:init_config)

    required = [:required]

    init_software_token = required unless software_token_initialized?
    init_owner = required unless serverconf && serverconf.owner
    init_server_code = required unless serverconf && serverconf.serverCode

    unless init_software_token || init_owner || init_server_code
      raise t('init.already_initialized')
    end

    validate_params({
      :owner_class => init_owner || [],
      :owner_code => init_owner || [],
      :server_code => init_server_code || [],
      :pin => init_software_token || [],
      :pin_repeat => init_software_token || []
    })

    new_serverconf = serverconf || ServerConfType.new

    if init_owner
      owner_id = ClientId.create(
        sdsb_instance,
        params[:owner_class],
        params[:owner_code], nil)

      unless get_member_name(params[:owner_class], params[:owner_code])
        warn_message = t('init.unregistered_member', {
          :member_class => params[:owner_class].upcase,
          :member_code => params[:owner_code]
        })
        warn("unregistered_member", warn_message)
      end

      owner_id = get_identifier(owner_id)

      owner = nil
      new_serverconf.client.each do |client|
        if client.identifier == owner_id
          owner = client
          break
        end
      end

      unless owner
        owner = ClientType.new
        owner.identifier = owner_id
        owner.clientStatus = ClientType::STATUS_SAVED
        owner.isAuthentication = "NOSSL"
        owner.conf = new_serverconf

        new_serverconf.client.add(owner)
      end

      new_serverconf.owner = owner
    end

    if init_server_code
      new_serverconf.serverCode = params[:server_code]
    end

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

    serverconf_save(new_serverconf)

    after_commit do
      import_services if x55_installed?
    end

    render_json
  end

  def member_classes
    authorize!(:init_config)

    classes = []

    if globalconf_initialized?
      GlobalConf::getMemberClasses(sdsb_instance).each do |memberClass|
        classes << memberClass
      end
    end

    render_json(classes)
  end

  def member_codes
    authorize!(:init_config)

    validate_params({
      :member_class => []
    })

    codes = Set.new

    if globalconf_initialized?
      GlobalConf::getMembers(sdsb_instance).each do |member|
        unless params[:member_class] &&
            params[:member_class] != member.id.memberClass
          codes << member.id.memberCode
        end
      end
    end

    render_json(codes)
  end

  def member_name
    authorize!(:init_config)

    validate_params({
      :owner_class => [],
      :owner_code => []
    })

    name = get_member_name(params[:owner_class], params[:owner_code])

    render_json(:name => name)
  end
end
