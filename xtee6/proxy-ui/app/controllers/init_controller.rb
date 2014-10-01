require 'net/http'

java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.CertificateType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.ClientType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.GlobalConfDistributorType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.ServerConfType
java_import Java::ee.cyber.sdsb.common.identifier.ClientId
java_import Java::ee.cyber.sdsb.commonui.SignerProxy

class InitController < ApplicationController

  skip_around_filter :transaction, :only =>
    [:init_globalconf, :member_classes, :member_codes, :member_name]

  skip_before_filter :check_conf, :read_server_id, :read_owner_name

  def index
    if request.xhr?
      # come back without ajax
      render_redirect
      return
    end

    if cannot?(:init_config)
      raise t('init.not_authorized')
    end

    if initialized?
      raise t('init.already_initialized')
    end

    unless globalconf_initialized? && serverconf &&
        !serverconf.globalConfDistributor.isEmpty
      @init_globalconf = true
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
        @owner_name = get_member_name(@owner_class, @owner_code)
      end

      @server_code = serverconf.serverCode
    end
  end

  def init_globalconf
    authorize!(:init_config)

    validate_params({
      :globalconf_url => [RequiredValidator.new],
      :globalconf_cert => [RequiredValidator.new]
    })

    # start transaction manually so that it is committed before
    # globalconf download
    transaction do
      new_serverconf = serverconf || ServerConfType.new

      cert = CertificateType.new
      cert.data = pem_to_der(params[:globalconf_cert].read).to_java_bytes

      distributor = GlobalConfDistributorType.new
      distributor.url = params[:globalconf_url]
      distributor.verificationCert = cert

      new_serverconf.globalConfDistributor.clear
      new_serverconf.globalConfDistributor.add(distributor)

      serverconf_save(new_serverconf)
    end

    begin
      download_globalconf
    rescue Exception => e
      transaction do
        serverconf.globalConfDistributor.clear
        serverconf_save
      end

      raise e
    end

    notice(t('init.globalconf_downloaded'))
    upload_success
  end

  def init_serverconf
    authorize!(:init_config)

    required = [RequiredValidator.new]

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

    new_serverconf = serverconf || ServerConfType.new

    if init_owner
      owner_id = ClientId.create(
        globalconf.root.instanceIdentifier,
        params[:owner_class],
        params[:owner_code], nil)

      unless get_member_name(params[:owner_class], params[:owner_code])
        raise t('init.member_not_found', :member => owner_id.toShortString)
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

    serverconf_save(new_serverconf)

    after_commit do
      import_services if x55_installed?
    end

    render_json
  end

  def member_classes
    authorize!(:init_config)

    classes = []

    if globalconf.exists?
      globalconf.root.memberClass.each do |memberClass|
        classes << memberClass.code
      end
    end

    render_json(classes)
  end

  def member_codes
    authorize!(:init_config)

    codes = []

    if globalconf.exists?
      globalconf.root.member.each do |member|
        codes << member.memberCode
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

  private

  def download_globalconf
    logger.info("Starting globalconf download")

    port = SystemProperties::getDistributedFilesAdminPort()
    uri = URI("http://localhost:#{port}/execute")

    response = Net::HTTP.get_response(uri)

    if response.code == '500'
      logger.error(response.body)
      raise t('init.globalconf_download_failed', :response => response.body)
    end
  end
end
